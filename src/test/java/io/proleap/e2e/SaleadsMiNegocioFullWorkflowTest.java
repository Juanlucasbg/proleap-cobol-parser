package io.proleap.e2e;

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
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final int DEFAULT_TIMEOUT_MS = 20_000;
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern GOOGLE_ACCOUNT_EMAIL_PATTERN = Pattern.compile("(?i)juanlucasbarbiergarzon@gmail\\.com");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
	private static final Pattern NEGOCIO_TEXT = Pattern.compile("(?i)negocio");
	private static final Pattern MI_NEGOCIO_TEXT = Pattern.compile("(?i)mi\\s+negocio");
	private static final Pattern AGREGAR_NEGOCIO_TEXT = Pattern.compile("(?i)agregar\\s+negocio");
	private static final Pattern ADMINISTRAR_NEGOCIOS_TEXT = Pattern.compile("(?i)administrar\\s+negocios");
	private static final Pattern CREAR_NEGOCIO_TEXT = Pattern.compile("(?i)crear\\s+nuevo\\s+negocio");

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		final String loginUrl = resolveLoginUrl();
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL (or SALEADS_URL / BASE_URL) to run this environment-agnostic E2E test.",
				loginUrl != null);

		final Path artifactsDir = createArtifactsDir();
		final Path reportPath = artifactsDir.resolve("saleads-mi-negocio-report.json");
		final Map<String, Boolean> statusByStep = initializeStatusMap();
		final Map<String, String> detailByStep = new LinkedHashMap<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create();
			 Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(isHeadless()));
			 BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000))) {
				final Page page = context.newPage();
				page.navigate(loginUrl);
				waitForUiToLoad(page);

				runStep("Login", statusByStep, detailByStep, () -> {
					loginWithGoogle(page);
					assertVisible("main application interface", firstVisible(Arrays.asList(
							page.locator("main"),
							page.locator("[role='main']"),
							page.getByText(Pattern.compile("(?i)(dashboard|panel|inicio|resumen)"))
					), 8_000, "main application container"));
					assertVisible("left sidebar navigation", firstVisible(Arrays.asList(
							page.locator("aside"),
							page.getByRole(AriaRole.NAVIGATION),
							page.getByText(NEGOCIO_TEXT)
					), 15_000, "left sidebar"));
					takeScreenshot(page, artifactsDir.resolve("01-dashboard-loaded.png"), false);
				});

				runStep("Mi Negocio menu", statusByStep, detailByStep, () -> {
					clickMiNegocioMenu(page);
					assertVisible("Agregar Negocio menu option", firstVisible(Arrays.asList(
							page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_TEXT)),
							page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_TEXT)),
							page.getByText(AGREGAR_NEGOCIO_TEXT)
					), 10_000, "Agregar Negocio option"));
					assertVisible("Administrar Negocios menu option", firstVisible(Arrays.asList(
							page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_TEXT)),
							page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_TEXT)),
							page.getByText(ADMINISTRAR_NEGOCIOS_TEXT)
					), 10_000, "Administrar Negocios option"));
					takeScreenshot(page, artifactsDir.resolve("02-mi-negocio-menu-expanded.png"), false);
				});

				runStep("Agregar Negocio modal", statusByStep, detailByStep, () -> {
					clickVisible(page, Arrays.asList(
							page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_TEXT)),
							page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_TEXT)),
							page.getByText(AGREGAR_NEGOCIO_TEXT)
					), "Agregar Negocio");

					final Locator modalTitle = firstVisible(Arrays.asList(
							page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(CREAR_NEGOCIO_TEXT)),
							page.getByText(CREAR_NEGOCIO_TEXT)
					), 10_000, "Crear Nuevo Negocio modal title");
					assertVisible("Crear Nuevo Negocio modal title", modalTitle);

					assertVisible("Nombre del Negocio input", firstVisible(Arrays.asList(
							page.getByLabel(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
							page.getByPlaceholder(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
							page.locator("input").filter(new Locator.FilterOptions().setHasText(Pattern.compile("(?i)nombre")))
					), 6_000, "Nombre del Negocio input"));

					assertVisible("Tienes 2 de 3 negocios text", firstVisible(Arrays.asList(
							page.getByText(Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios")),
							page.getByText(Pattern.compile("(?i)2\\s+de\\s+3\\s+negocios"))
					), 6_000, "business quota text"));

					final Locator cancelButton = firstVisible(Arrays.asList(
							page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar"))),
							page.getByText(Pattern.compile("(?i)cancelar"))
					), 6_000, "Cancelar button");
					assertVisible("Crear Negocio button", firstVisible(Arrays.asList(
							page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear\\s+negocio"))),
							page.getByText(Pattern.compile("(?i)crear\\s+negocio"))
					), 6_000, "Crear Negocio button"));

					takeScreenshot(page, artifactsDir.resolve("03-agregar-negocio-modal.png"), false);

					fillOptionalBusinessName(page);
					clickAndWait(page, cancelButton, "Cancelar");
				});

				runStep("Administrar Negocios view", statusByStep, detailByStep, () -> {
					expandMiNegocioIfCollapsed(page);
					clickVisible(page, Arrays.asList(
							page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_TEXT)),
							page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_TEXT)),
							page.getByText(ADMINISTRAR_NEGOCIOS_TEXT)
					), "Administrar Negocios");
					waitForUiToLoad(page);

					assertVisible("Información General section", page.getByText(Pattern.compile("(?i)informaci[oó]n\\s+general")).first());
					assertVisible("Detalles de la Cuenta section", page.getByText(Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta")).first());
					assertVisible("Tus Negocios section", page.getByText(Pattern.compile("(?i)tus\\s+negocios")).first());
					assertVisible("Sección Legal section", page.getByText(Pattern.compile("(?i)secci[oó]n\\s+legal")).first());

					takeScreenshot(page, artifactsDir.resolve("04-administrar-negocios-view-full.png"), true);
				});

				runStep("Información General", statusByStep, detailByStep, () -> {
					assertVisible("Información General section title", page.getByText(Pattern.compile("(?i)informaci[oó]n\\s+general")).first());
					assertVisible("user name marker", firstVisible(Arrays.asList(
							page.getByText(Pattern.compile("(?i)juan")),
							page.getByText(Pattern.compile("(?i)usuario")),
							page.getByText(Pattern.compile("(?i)nombre"))
					), 5_000, "user name marker"));
					assertVisible("user email", firstVisible(Arrays.asList(
							page.getByText(GOOGLE_ACCOUNT_EMAIL_PATTERN),
							page.getByText(EMAIL_PATTERN)
					), 8_000, "user email"));
					assertVisible("BUSINESS PLAN label", page.getByText(Pattern.compile("(?i)business\\s+plan")).first());
					assertVisible("Cambiar Plan button", firstVisible(Arrays.asList(
							page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cambiar\\s+plan"))),
							page.getByText(Pattern.compile("(?i)cambiar\\s+plan"))
					), 8_000, "Cambiar Plan button"));
				});

				runStep("Detalles de la Cuenta", statusByStep, detailByStep, () -> {
					assertVisible("Cuenta creada", page.getByText(Pattern.compile("(?i)cuenta\\s+creada")).first());
					assertVisible("Estado activo", page.getByText(Pattern.compile("(?i)estado\\s+activo")).first());
					assertVisible("Idioma seleccionado", page.getByText(Pattern.compile("(?i)idioma\\s+seleccionado")).first());
				});

				runStep("Tus Negocios", statusByStep, detailByStep, () -> {
					assertVisible("Tus Negocios section title", page.getByText(Pattern.compile("(?i)tus\\s+negocios")).first());
					assertVisible("business list/container", firstVisible(Arrays.asList(
							page.locator("[data-testid*='business']"),
							page.locator("ul,table,div").filter(new Locator.FilterOptions().setHasText(Pattern.compile("(?i)negocio"))),
							page.getByText(Pattern.compile("(?i)negocio"))
					), 8_000, "business list"));
					assertVisible("Agregar Negocio button", firstVisible(Arrays.asList(
							page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_TEXT)),
							page.getByText(AGREGAR_NEGOCIO_TEXT)
					), 8_000, "Agregar Negocio button"));
					assertVisible("Tienes 2 de 3 negocios text", firstVisible(Arrays.asList(
							page.getByText(Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios")),
							page.getByText(Pattern.compile("(?i)2\\s+de\\s+3\\s+negocios"))
					), 8_000, "business quota text"));
				});

				runStep("Términos y Condiciones", statusByStep, detailByStep, () -> {
					final String legalUrl = openAndValidateLegalPage(
							context,
							page,
							Pattern.compile("(?i)t[eé]rminos\\s+y\\s+condiciones"),
							Pattern.compile("(?i)t[eé]rminos\\s+y\\s+condiciones"),
							artifactsDir.resolve("08-terminos-y-condiciones.png"));
					legalUrls.put("terminosYCondicionesUrl", legalUrl);
				});

				runStep("Política de Privacidad", statusByStep, detailByStep, () -> {
					final String legalUrl = openAndValidateLegalPage(
							context,
							page,
							Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad"),
							Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad"),
							artifactsDir.resolve("09-politica-de-privacidad.png"));
					legalUrls.put("politicaDePrivacidadUrl", legalUrl);
				});
		}

		writeReport(reportPath, statusByStep, detailByStep, legalUrls, artifactsDir);
		assertNoStepFailures(statusByStep, reportPath);
	}

	private void loginWithGoogle(final Page page) {
		final Locator loginButton = firstVisible(Arrays.asList(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)(sign\\s*in|iniciar\\s*sesi[oó]n|continuar|acceder).*google"))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)(sign\\s*in|iniciar\\s*sesi[oó]n|continuar|acceder).*google"))),
				page.getByText(Pattern.compile("(?i)(sign\\s*in|iniciar\\s*sesi[oó]n|continuar).*google")),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)google")))
		), 15_000, "Google login button");

		Page popupPage = null;
		try {
			popupPage = page.waitForPopup(
					new Page.WaitForPopupOptions().setTimeout(8_000),
					() -> loginButton.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS)));
		} catch (PlaywrightException e) {
			// The login flow may stay in the same tab or open too quickly.
		}
		waitForUiToLoad(page);

		if (popupPage != null) {
			waitForUiToLoad(popupPage);
			selectGoogleAccountIfVisible(popupPage);
		}

		selectGoogleAccountIfVisible(page);
		waitForUiToLoad(page);
	}

	private void selectGoogleAccountIfVisible(final Page candidatePage) {
		try {
			final Locator accountOption = firstVisible(Arrays.asList(
					candidatePage.getByText(GOOGLE_ACCOUNT_EMAIL_PATTERN),
					candidatePage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(GOOGLE_ACCOUNT_EMAIL_PATTERN)),
					candidatePage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GOOGLE_ACCOUNT_EMAIL_PATTERN))
			), 5_000, "Google account selector");
			clickAndWait(candidatePage, accountOption, GOOGLE_ACCOUNT_EMAIL);
		} catch (AssertionError ignored) {
			// Account chooser can be skipped when Google session is already active.
		}
	}

	private void clickMiNegocioMenu(final Page page) {
		clickVisible(page, Arrays.asList(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEGOCIO_TEXT)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(NEGOCIO_TEXT)),
				page.getByText(NEGOCIO_TEXT)
		), "Negocio");

		clickVisible(page, Arrays.asList(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(MI_NEGOCIO_TEXT)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(MI_NEGOCIO_TEXT)),
				page.getByText(MI_NEGOCIO_TEXT)
		), "Mi Negocio");
	}

	private void expandMiNegocioIfCollapsed(final Page page) {
		final Locator addBusinessOption = page.getByText(AGREGAR_NEGOCIO_TEXT).first();
		if (!isVisible(addBusinessOption)) {
			clickVisible(page, Arrays.asList(
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(MI_NEGOCIO_TEXT)),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(MI_NEGOCIO_TEXT)),
					page.getByText(MI_NEGOCIO_TEXT)
			), "Mi Negocio (expand)");
		}
	}

	private void fillOptionalBusinessName(final Page page) {
		final Locator nameInput = firstVisible(Arrays.asList(
				page.getByLabel(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
				page.getByPlaceholder(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
				page.locator("input[name*='nombre'], input[id*='nombre']")
		), 5_000, "Nombre del Negocio input");
		nameInput.click();
		waitForUiToLoad(page);
		nameInput.fill("Negocio Prueba Automatización");
		waitForUiToLoad(page);
	}

	private String openAndValidateLegalPage(
			final BrowserContext context,
			final Page appPage,
			final Pattern linkPattern,
			final Pattern headingPattern,
			final Path screenshotPath) {
		final Locator legalLink = firstVisible(Arrays.asList(
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkPattern)),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(linkPattern)),
				appPage.getByText(linkPattern)
		), 10_000, "Legal link: " + linkPattern.pattern());
		final String appUrlBefore = appPage.url();

		Page legalPage = null;
		try {
			legalPage = context.waitForPage(
					new BrowserContext.WaitForPageOptions().setTimeout(7_000),
					() -> legalLink.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS)));
		} catch (PlaywrightException e) {
			// Link can navigate in the same tab.
		}
		waitForUiToLoad(appPage);

		if (legalPage != null) {
			waitForUiToLoad(legalPage);
			assertVisible("legal heading", firstVisible(Arrays.asList(
					legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)),
					legalPage.getByText(headingPattern)
			), 10_000, "Legal heading"));
			assertVisible("legal content text", firstVisible(Arrays.asList(
					legalPage.locator("p,article,main,section").filter(new Locator.FilterOptions().setHasText(
							Pattern.compile("(?i)(condiciones|t[eé]rminos|privacidad|datos|uso|informaci[oó]n)")
					)),
					legalPage.getByText(Pattern.compile("(?i)(condiciones|t[eé]rminos|privacidad|datos|uso|informaci[oó]n)"))
			), 10_000, "Legal content"));
			takeScreenshot(legalPage, screenshotPath, true);

			final String finalUrl = legalPage.url();
			legalPage.close();
			appPage.bringToFront();
			waitForUiToLoad(appPage);
			return finalUrl;
		}

		waitForUiToLoad(appPage);
		assertVisible("legal heading", firstVisible(Arrays.asList(
				appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)),
				appPage.getByText(headingPattern)
		), 10_000, "Legal heading (same tab)"));
		assertVisible("legal content text", firstVisible(Arrays.asList(
				appPage.locator("p,article,main,section").filter(new Locator.FilterOptions().setHasText(
						Pattern.compile("(?i)(condiciones|t[eé]rminos|privacidad|datos|uso|informaci[oó]n)")
				)),
				appPage.getByText(Pattern.compile("(?i)(condiciones|t[eé]rminos|privacidad|datos|uso|informaci[oó]n)"))
		), 10_000, "Legal content (same tab)"));
		takeScreenshot(appPage, screenshotPath, true);

		final String finalUrl = appPage.url();
		try {
			appPage.goBack();
			waitForUiToLoad(appPage);
		} catch (PlaywrightException ignored) {
			appPage.navigate(appUrlBefore);
			waitForUiToLoad(appPage);
		}
		return finalUrl;
	}

	private void clickVisible(final Page page, final List<Locator> candidates, final String actionName) {
		final Locator target = firstVisible(candidates, 10_000, actionName);
		clickAndWait(page, target, actionName);
	}

	private void clickAndWait(final Page page, final Locator locator, final String actionName) {
		try {
			locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (PlaywrightException e) {
			throw new AssertionError("Unable to click \"" + actionName + "\": " + e.getMessage(), e);
		}
		waitForUiToLoad(page);
	}

	private Locator firstVisible(final List<Locator> candidates, final int timeoutMs, final String description) {
		final List<String> errors = new ArrayList<>();
		for (final Locator candidate : candidates) {
			final Locator current = candidate.first();
			try {
				current.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
				return current;
			} catch (PlaywrightException e) {
				errors.add(e.getMessage());
			}
		}
		throw new AssertionError("Could not find visible element for: " + description + ". Candidate errors: " + errors.size());
	}

	private void waitForUiToLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (PlaywrightException ignored) {
			// Some SPA transitions never trigger a full load event.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5_000));
		} catch (PlaywrightException ignored) {
			// Network can stay active due to analytics/websockets.
		}
		page.waitForTimeout(700);
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator.isVisible(new Locator.IsVisibleOptions().setTimeout(2_000));
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private void takeScreenshot(final Page page, final Path targetPath, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(targetPath).setFullPage(fullPage));
	}

	private void assertVisible(final String label, final Locator locator) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (PlaywrightException e) {
			throw new AssertionError("Expected visible element not found for: " + label, e);
		}
	}

	private void runStep(
			final String stepName,
			final Map<String, Boolean> statusByStep,
			final Map<String, String> detailByStep,
			final StepAction stepAction) {
		try {
			stepAction.run();
			statusByStep.put(stepName, true);
			detailByStep.put(stepName, "PASS");
		} catch (Throwable t) {
			statusByStep.put(stepName, false);
			detailByStep.put(stepName, sanitize(t.getMessage()));
		}
	}

	private void assertNoStepFailures(final Map<String, Boolean> statusByStep, final Path reportPath) {
		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, Boolean> entry : statusByStep.entrySet()) {
			if (!entry.getValue()) {
				failedSteps.add(entry.getKey());
			}
		}
		if (!failedSteps.isEmpty()) {
			Assert.fail("SaleADS Mi Negocio workflow failed. Review report: " + reportPath + ". Failed steps: " + failedSteps);
		}
	}

	private Map<String, Boolean> initializeStatusMap() {
		final Map<String, Boolean> statusByStep = new LinkedHashMap<>();
		statusByStep.put("Login", false);
		statusByStep.put("Mi Negocio menu", false);
		statusByStep.put("Agregar Negocio modal", false);
		statusByStep.put("Administrar Negocios view", false);
		statusByStep.put("Información General", false);
		statusByStep.put("Detalles de la Cuenta", false);
		statusByStep.put("Tus Negocios", false);
		statusByStep.put("Términos y Condiciones", false);
		statusByStep.put("Política de Privacidad", false);
		return statusByStep;
	}

	private Path createArtifactsDir() throws IOException {
		final String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT));
		final Path artifactsDir = Paths.get("target", "saleads-mi-negocio", timestamp);
		Files.createDirectories(artifactsDir);
		return artifactsDir;
	}

	private String resolveLoginUrl() {
		for (final String key : Arrays.asList("SALEADS_LOGIN_URL", "SALEADS_URL", "BASE_URL")) {
			final String value = System.getenv(key);
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private boolean isHeadless() {
		final String value = System.getenv("SALEADS_HEADLESS");
		if (value == null || value.trim().isEmpty()) {
			return true;
		}
		return !"false".equalsIgnoreCase(value.trim());
	}

	private void writeReport(
			final Path reportPath,
			final Map<String, Boolean> statusByStep,
			final Map<String, String> detailByStep,
			final Map<String, String> legalUrls,
			final Path artifactsDir) throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("{\n");
		builder.append("  \"generatedAt\": \"").append(OffsetDateTime.now()).append("\",\n");
		builder.append("  \"artifactsDir\": \"").append(escapeJson(artifactsDir.toString())).append("\",\n");
		builder.append("  \"steps\": [\n");

		int index = 0;
		final int size = statusByStep.size();
		for (final Map.Entry<String, Boolean> entry : statusByStep.entrySet()) {
			final String stepName = entry.getKey();
			final String detail = detailByStep.getOrDefault(stepName, "Not executed");
			builder.append("    {\n");
			builder.append("      \"name\": \"").append(escapeJson(stepName)).append("\",\n");
			builder.append("      \"status\": \"").append(entry.getValue() ? "PASS" : "FAIL").append("\",\n");
			builder.append("      \"detail\": \"").append(escapeJson(detail)).append("\"\n");
			builder.append("    }");
			if (index < size - 1) {
				builder.append(",");
			}
			builder.append("\n");
			index++;
		}

		builder.append("  ],\n");
		builder.append("  \"evidence\": {\n");
		builder.append("    \"terminosYCondicionesUrl\": \"")
				.append(escapeJson(legalUrls.getOrDefault("terminosYCondicionesUrl", ""))).append("\",\n");
		builder.append("    \"politicaDePrivacidadUrl\": \"")
				.append(escapeJson(legalUrls.getOrDefault("politicaDePrivacidadUrl", ""))).append("\"\n");
		builder.append("  }\n");
		builder.append("}\n");

		Files.write(reportPath, builder.toString().getBytes(StandardCharsets.UTF_8));
	}

	private String sanitize(final String value) {
		if (value == null || value.trim().isEmpty()) {
			return "Unexpected error";
		}
		return value.replaceAll("[\\r\\n]+", " ").trim();
	}

	private String escapeJson(final String value) {
		return value
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\t", "\\t");
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
