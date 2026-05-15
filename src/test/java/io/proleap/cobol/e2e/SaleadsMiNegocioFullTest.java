package io.proleap.cobol.e2e;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

public class SaleadsMiNegocioFullTest {

	private static final Pattern GOOGLE_BUTTON_PATTERN = Pattern
			.compile("(?i)(sign in with google|iniciar sesi[oó]n con google|continuar con google|google)");
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String TEST_BUSINESS_NAME = "Negocio Prueba Automatización";
	private static final int DEFAULT_TIMEOUT_MS = 15000;

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private Path artifactsDir;
	private Page appPage;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String loginUrl = env("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL with the login page of the target environment.",
				loginUrl != null && !loginUrl.isBlank());

		artifactsDir = Paths.get(Optional.ofNullable(env("SALEADS_E2E_ARTIFACTS_DIR")).orElse("target/saleads-e2e"));
		Files.createDirectories(artifactsDir);

		final boolean headless = Boolean.parseBoolean(Optional.ofNullable(env("SALEADS_HEADLESS")).orElse("true"));

		try (Playwright playwright = Playwright.create();
				Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
				BrowserContext context = browser.newContext(
						new Browser.NewContextOptions().setViewportSize(1600, 1200))) {

			appPage = context.newPage();
			appPage.navigate(loginUrl);
			waitForUiLoad(appPage);

			executeStep("Login", () -> performGoogleLogin(context));
			executeStep("Mi Negocio menu", this::openMiNegocioMenu);
			executeStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
			executeStep("Administrar Negocios view", this::openAdministrarNegocios);
			executeStep("Información General", this::validateInformacionGeneral);
			executeStep("Detalles de la Cuenta", this::validateDetallesCuenta);
			executeStep("Tus Negocios", this::validateTusNegocios);
			executeStep("Términos y Condiciones",
					() -> validateLegalLink(context, "Términos y Condiciones", "Términos y Condiciones",
							"08_terminos_y_condiciones.png"));
			executeStep("Política de Privacidad", () -> validateLegalLink(context, "Política de Privacidad",
					"Política de Privacidad", "09_politica_de_privacidad.png"));
		} finally {
			writeFinalReport();
		}

		final List<String> failedSteps = report.entrySet().stream().filter(entry -> !entry.getValue().passed)
				.map(Map.Entry::getKey).collect(Collectors.toList());
		assertTrue("One or more validations failed: " + failedSteps, failedSteps.isEmpty());
	}

	private void performGoogleLogin(final BrowserContext context) throws IOException {
		if (!isSidebarVisible()) {
			final Locator loginButton = firstVisible(Arrays.asList(
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GOOGLE_BUTTON_PATTERN)),
					appPage.getByText(GOOGLE_BUTTON_PATTERN).first()), DEFAULT_TIMEOUT_MS);

			assertNotNull("Google login button not found.", loginButton);

			Page popupPage = null;
			try {
				popupPage = context.waitForPage(() -> loginButton.click(),
						new BrowserContext.WaitForPageOptions().setTimeout(7000));
			} catch (final PlaywrightException ignored) {
				// Same-tab login flow.
			}

			waitForUiLoad(appPage);

			if (popupPage != null) {
				handleGoogleAccountSelection(popupPage);
				appPage.bringToFront();
				waitForUiLoad(appPage);
			} else if (appPage.url().contains("accounts.google.com")) {
				handleGoogleAccountSelection(appPage);
				waitForUiLoad(appPage);
			}
		}

		assertTrue("Main application interface did not appear after login.", isSidebarVisible());
		captureScreenshot(appPage, "01_dashboard_loaded.png", true, report.get("Login"));
	}

	private void openMiNegocioMenu() throws IOException {
		final Locator negocio = firstVisible(Arrays.asList(appPage.getByText(Pattern.compile("(?i)^Negocio$")).first(),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Negocio$")))
						.first()), 5000);
		if (negocio != null) {
			clickAndWait(negocio);
		}

		final Locator miNegocio = firstVisible(
				Arrays.asList(appPage.getByText(Pattern.compile("(?i)^Mi Negocio$")).first(),
						appPage.getByRole(AriaRole.BUTTON,
								new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Mi Negocio$"))).first()),
				DEFAULT_TIMEOUT_MS);
		assertNotNull("Option 'Mi Negocio' was not found in sidebar.", miNegocio);
		clickAndWait(miNegocio);

		assertVisible(firstVisible(Arrays.asList(appPage.getByText(Pattern.compile("(?i)^Agregar Negocio$")).first()),
				DEFAULT_TIMEOUT_MS), "Option 'Agregar Negocio' is not visible.");
		assertVisible(
				firstVisible(Arrays.asList(appPage.getByText(Pattern.compile("(?i)^Administrar Negocios$")).first()),
						DEFAULT_TIMEOUT_MS),
				"Option 'Administrar Negocios' is not visible.");

		captureScreenshot(appPage, "02_mi_negocio_menu_expanded.png", false, report.get("Mi Negocio menu"));
	}

	private void validateAgregarNegocioModal() throws IOException {
		final Locator agregarNegocio = firstVisible(Arrays.asList(appPage.getByText(Pattern.compile("(?i)^Agregar Negocio$")).first(),
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Agregar Negocio$"))).first()),
				DEFAULT_TIMEOUT_MS);
		assertNotNull("Could not find 'Agregar Negocio' option.", agregarNegocio);
		clickAndWait(agregarNegocio);

		final Locator modalTitle = appPage.getByText(Pattern.compile("(?i)Crear Nuevo Negocio")).first();
		waitVisible(modalTitle, DEFAULT_TIMEOUT_MS);

		final Locator nombreInput = firstVisible(Arrays.asList(
				appPage.getByLabel(Pattern.compile("(?i)Nombre del Negocio")).first(),
				appPage.getByPlaceholder(Pattern.compile("(?i)Nombre del Negocio")).first()), DEFAULT_TIMEOUT_MS);
		assertVisible(nombreInput, "Input 'Nombre del Negocio' was not found.");

		assertVisible(appPage.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")).first(),
				"Usage text 'Tienes 2 de 3 negocios' was not found.");
		assertVisible(appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")).first(),
				"Button 'Cancelar' was not found.");
		assertVisible(appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio")).first(),
				"Button 'Crear Negocio' was not found.");

		captureScreenshot(appPage, "03_agregar_negocio_modal.png", false, report.get("Agregar Negocio modal"));

		nombreInput.fill(TEST_BUSINESS_NAME);
		clickAndWait(appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")).first());
		modalTitle.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(DEFAULT_TIMEOUT_MS));
	}

	private void openAdministrarNegocios() throws IOException {
		expandMiNegocioMenuIfNeeded();

		final Locator administrar = firstVisible(Arrays.asList(
				appPage.getByText(Pattern.compile("(?i)^Administrar Negocios$")).first(),
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Administrar Negocios$"))).first()),
				DEFAULT_TIMEOUT_MS);
		assertNotNull("Could not find 'Administrar Negocios' option.", administrar);
		clickAndWait(administrar);

		assertVisible(sectionByHeading("Información General"), "Section 'Información General' was not found.");
		assertVisible(sectionByHeading("Detalles de la Cuenta"), "Section 'Detalles de la Cuenta' was not found.");
		assertVisible(sectionByHeading("Tus Negocios"), "Section 'Tus Negocios' was not found.");
		assertVisible(sectionByHeading("Sección Legal"), "Section 'Sección Legal' was not found.");

		captureScreenshot(appPage, "04_administrar_negocios_full_page.png", true, report.get("Administrar Negocios view"));
	}

	private void validateInformacionGeneral() {
		final Locator section = sectionByHeading("Información General");
		assertVisible(section, "Section 'Información General' is not visible.");

		final String sectionText = section.innerText();
		assertTrue("User email is not visible in 'Información General'.",
				EMAIL_PATTERN.matcher(sectionText).find() || sectionText.contains(ACCOUNT_EMAIL));
		assertTrue("User name is not visible in 'Información General'.", containsLikelyName(sectionText));

		assertVisible(section.getByText(Pattern.compile("(?i)BUSINESS PLAN")).first(),
				"Text 'BUSINESS PLAN' is not visible.");
		assertVisible(section.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Cambiar Plan")).first(),
				"Button 'Cambiar Plan' is not visible.");
	}

	private void validateDetallesCuenta() {
		final Locator section = sectionByHeading("Detalles de la Cuenta");
		assertVisible(section, "Section 'Detalles de la Cuenta' is not visible.");

		assertVisible(section.getByText(Pattern.compile("(?i)Cuenta creada")).first(),
				"Text 'Cuenta creada' is not visible.");
		assertVisible(section.getByText(Pattern.compile("(?i)Estado activo")).first(),
				"Text 'Estado activo' is not visible.");
		assertVisible(section.getByText(Pattern.compile("(?i)Idioma seleccionado")).first(),
				"Text 'Idioma seleccionado' is not visible.");
	}

	private void validateTusNegocios() {
		final Locator section = sectionByHeading("Tus Negocios");
		assertVisible(section, "Section 'Tus Negocios' is not visible.");

		assertVisible(section.getByText(Pattern.compile("(?i)Agregar Negocio")).first(),
				"Button/Text 'Agregar Negocio' is not visible in 'Tus Negocios'.");
		assertVisible(section.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")).first(),
				"Text 'Tienes 2 de 3 negocios' is not visible in 'Tus Negocios'.");

		final boolean hasListItems = section.locator("li, [role='listitem'], table tbody tr").count() > 0;
		final long nonEmptyLines = Arrays.stream(section.innerText().split("\\R")).map(String::trim).filter(line -> !line.isBlank())
				.count();
		assertTrue("Business list is not visible in 'Tus Negocios'.", hasListItems || nonEmptyLines > 4);
	}

	private void validateLegalLink(final BrowserContext context, final String linkText, final String headingText,
			final String screenshotFileName) throws IOException {
		final Locator legalSection = sectionByHeading("Sección Legal");
		assertVisible(legalSection, "Section 'Sección Legal' is not visible.");

		final Locator link = firstVisible(Arrays.asList(
				legalSection.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(linkText)).first(),
				legalSection.getByText(Pattern.compile("(?i)^" + Pattern.quote(linkText) + "$")).first(),
				appPage.getByText(Pattern.compile("(?i)^" + Pattern.quote(linkText) + "$")).first()), DEFAULT_TIMEOUT_MS);
		assertNotNull("Link '" + linkText + "' was not found.", link);

		final String originalUrl = appPage.url();
		Page targetPage = appPage;
		Page newTab = null;
		try {
			newTab = context.waitForPage(() -> link.click(), new BrowserContext.WaitForPageOptions().setTimeout(5000));
			targetPage = newTab;
		} catch (final PlaywrightException ignored) {
			link.click();
		}

		waitForUiLoad(targetPage);

		final Locator legalHeading = targetPage.getByRole(AriaRole.HEADING,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(headingText)))).first();
		assertVisible(legalHeading, "Heading '" + headingText + "' is not visible.");

		final String legalText = targetPage.locator("main, article, body").first().innerText().trim();
		assertTrue("Legal content text is not visible for '" + linkText + "'.", legalText.length() > 120);

		final StepResult stepResult = report.get(linkText);
		captureScreenshot(targetPage, screenshotFileName, true, stepResult);
		stepResult.finalUrl = targetPage.url();

		if (newTab != null) {
			newTab.close();
			appPage.bringToFront();
		} else if (!appPage.url().equals(originalUrl)) {
			appPage.goBack();
		}
		waitForUiLoad(appPage);
	}

	private void handleGoogleAccountSelection(final Page page) {
		waitForUiLoad(page);
		final Locator account = firstVisible(Arrays.asList(page.getByText(ACCOUNT_EMAIL).first(),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(ACCOUNT_EMAIL)).first(),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ACCOUNT_EMAIL)).first()), 12000);

		if (account != null) {
			account.click();
			waitForUiLoad(page);
		}
	}

	private void executeStep(final String stepName, final StepAction action) {
		final StepResult result = new StepResult();
		report.put(stepName, result);
		try {
			action.execute();
			result.passed = true;
			result.details = "PASS";
		} catch (final Throwable error) {
			result.passed = false;
			result.details = Optional.ofNullable(error.getMessage()).orElse(error.getClass().getSimpleName());
		}
	}

	private void expandMiNegocioMenuIfNeeded() {
		final Locator administrar = appPage.getByText(Pattern.compile("(?i)^Administrar Negocios$")).first();
		if (isVisible(administrar, 1000)) {
			return;
		}

		final Locator negocio = firstVisible(Arrays.asList(appPage.getByText(Pattern.compile("(?i)^Negocio$")).first(),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Negocio$")))
						.first()), 3000);
		if (negocio != null) {
			clickAndWait(negocio);
		}

		final Locator miNegocio = firstVisible(Arrays.asList(appPage.getByText(Pattern.compile("(?i)^Mi Negocio$")).first(),
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Mi Negocio$"))).first()), 3000);
		if (miNegocio != null) {
			clickAndWait(miNegocio);
		}
	}

	private void clickAndWait(final Locator locator) {
		locator.click();
		waitForUiLoad(appPage);
	}

	private void waitForUiLoad(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (final PlaywrightException ignored) {
			// Network can stay active in SPAs.
		}
		page.waitForTimeout(700);
	}

	private Locator sectionByHeading(final String headingText) {
		final Locator heading = appPage
				.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^" + Pattern.quote(headingText) + "$")))
				.first();
		if (isVisible(heading, 2000)) {
			return heading.locator("xpath=ancestor::*[self::section or self::div][1]");
		}

		return appPage.locator("section, div").filter(new Locator.FilterOptions().setHasText(headingText)).first();
	}

	private void captureScreenshot(final Page page, final String fileName, final boolean fullPage, final StepResult stepResult)
			throws IOException {
		final Path screenshotPath = artifactsDir.resolve(fileName);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
		stepResult.screenshots.add(screenshotPath.toString());
	}

	private void waitVisible(final Locator locator, final int timeoutMs) {
		locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			return locator.isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMs));
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void assertVisible(final Locator locator, final String failureMessage) {
		assertNotNull(failureMessage, locator);
		waitVisible(locator, DEFAULT_TIMEOUT_MS);
	}

	private Locator firstVisible(final List<Locator> candidates, final int timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			for (final Locator candidate : candidates) {
				if (candidate != null && isVisible(candidate, 200)) {
					return candidate;
				}
			}
			if (appPage != null) {
				appPage.waitForTimeout(200);
			}
		}
		return null;
	}

	private boolean isSidebarVisible() {
		return isVisible(appPage.locator("aside, nav").first(), 4000)
				&& isVisible(appPage.getByText(Pattern.compile("(?i)(Negocio|Mi Negocio)")).first(), 4000);
	}

	private boolean containsLikelyName(final String sectionText) {
		for (final String rawLine : sectionText.split("\\R")) {
			final String line = rawLine.trim();
			if (line.isBlank() || line.contains("@")) {
				continue;
			}

			final String normalized = line.toUpperCase(Locale.ROOT);
			if (normalized.contains("INFORMACIÓN GENERAL") || normalized.contains("BUSINESS PLAN")
					|| normalized.contains("CAMBIAR PLAN")) {
				continue;
			}

			if (line.length() >= 3 && Pattern.compile(".*[A-Za-zÁÉÍÓÚÑáéíóúñ].*").matcher(line).matches()) {
				return true;
			}
		}
		return false;
	}

	private void writeFinalReport() throws IOException {
		final Path reportPath = artifactsDir.resolve("saleads_mi_negocio_full_report.json");
		final StringBuilder builder = new StringBuilder();
		builder.append("{\n");
		builder.append("  \"name\": \"saleads_mi_negocio_full_test\",\n");
		builder.append("  \"results\": {\n");

		int index = 0;
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (index++ > 0) {
				builder.append(",\n");
			}
			builder.append("    \"").append(escapeJson(entry.getKey())).append("\": {\n");
			builder.append("      \"status\": \"").append(entry.getValue().passed ? "PASS" : "FAIL").append("\",\n");
			builder.append("      \"details\": \"").append(escapeJson(entry.getValue().details)).append("\",\n");
			builder.append("      \"screenshots\": [");
			for (int i = 0; i < entry.getValue().screenshots.size(); i++) {
				if (i > 0) {
					builder.append(", ");
				}
				builder.append("\"").append(escapeJson(entry.getValue().screenshots.get(i))).append("\"");
			}
			builder.append("]");
			if (entry.getValue().finalUrl != null) {
				builder.append(",\n");
				builder.append("      \"finalUrl\": \"").append(escapeJson(entry.getValue().finalUrl)).append("\"\n");
			} else {
				builder.append("\n");
			}
			builder.append("    }");
		}

		builder.append("\n  }\n");
		builder.append("}\n");
		Files.writeString(reportPath, builder.toString(), StandardCharsets.UTF_8);
	}

	private String escapeJson(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}

	private String env(final String key) {
		return System.getenv(key);
	}

	@FunctionalInterface
	private interface StepAction {
		void execute() throws Exception;
	}

	private static final class StepResult {
		private boolean passed;
		private String details = "";
		private final List<String> screenshots = new ArrayList<>();
		private String finalUrl;
	}
}
