package io.proleap.e2e.saleads;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern GOOGLE_LOGIN_TEXT = Pattern
			.compile("(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s*sesi[oó]n\\s*con\\s*google|continuar\\s*con\\s*google|google)");
	private static final Pattern MENU_NEGOCIO_TEXT = Pattern.compile("(?i)^\\s*Negocio\\s*$");
	private static final Pattern MI_NEGOCIO_TEXT = Pattern.compile("(?i)^\\s*Mi\\s+Negocio\\s*$");
	private static final Pattern AGREGAR_NEGOCIO_TEXT = Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$");
	private static final Pattern ADMINISTRAR_NEGOCIOS_TEXT = Pattern.compile("(?i)^\\s*Administrar\\s+Negocios\\s*$");
	private static final Pattern ACCOUNT_EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final int DEFAULT_TIMEOUT_MS = 15000;

	private final Map<String, String> reportStatus = createDefaultReportStatus();
	private String terminosUrl = "N/A";
	private String politicaUrl = "N/A";
	private Path evidenceDir;
	private Path screenshotsDir;

	@Before
	public void checkConfiguration() {
		final boolean e2eEnabled = Boolean.parseBoolean(resolveConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue(
				"Set -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true to run this E2E workflow.",
				e2eEnabled);

		final String loginUrl = resolveConfig("saleads.login.url", "SALEADS_LOGIN_URL", "").trim();
		Assume.assumeTrue(
				"Set -Dsaleads.login.url=<url> or SALEADS_LOGIN_URL to run this E2E workflow.",
				!loginUrl.isEmpty());
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String loginUrl = resolveConfig("saleads.login.url", "SALEADS_LOGIN_URL", "").trim();
		createEvidenceDirectories();

		Throwable failure = null;

		try (Playwright playwright = Playwright.create()) {
			final boolean headless = Boolean
					.parseBoolean(resolveConfig("saleads.headless", "SALEADS_HEADLESS", "true"));
			try (Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless));
					BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 900))) {
				final Page appPage = context.newPage();
				appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
				waitForUi(appPage);

				executeStepLogin(appPage);
				executeStepOpenMiNegocioMenu(appPage);
				executeStepAgregarNegocioModal(appPage);
				executeStepAdministrarNegocios(appPage);
				executeStepInformacionGeneral(appPage);
				executeStepDetallesCuenta(appPage);
				executeStepTusNegocios(appPage);
				executeStepTerminos(appPage);
				executeStepPoliticaPrivacidad(appPage);
			}
		} catch (Throwable throwable) {
			failure = throwable;
			reportCurrentFailure(throwable);
		} finally {
			writeReportFile();
		}

		if (failure != null) {
			throw new AssertionError(
					"SaleADS Mi Negocio workflow failed. Inspect evidence at: " + evidenceDir.toAbsolutePath(),
					failure);
		}
	}

	private void executeStepLogin(final Page appPage) throws IOException {
		setCurrentStep("Login");

		final Locator loginButton = findVisibleClickable(appPage, GOOGLE_LOGIN_TEXT);
		final Page googlePage = clickAndGetNewTabOrCurrent(appPage, loginButton);

		selectGoogleAccountIfVisible(googlePage);
		waitForSidebar(appPage);
		takeScreenshot(appPage, "01-dashboard-loaded.png", false);

		reportStatus.put("Login", "PASS");
	}

	private void executeStepOpenMiNegocioMenu(final Page appPage) throws IOException {
		setCurrentStep("Mi Negocio menu");

		assertTextVisible(appPage, MENU_NEGOCIO_TEXT);
		clickAndWait(appPage, findVisibleClickable(appPage, MI_NEGOCIO_TEXT));
		assertTextVisible(appPage, AGREGAR_NEGOCIO_TEXT);
		assertTextVisible(appPage, ADMINISTRAR_NEGOCIOS_TEXT);
		takeScreenshot(appPage, "02-mi-negocio-menu-expanded.png", false);

		reportStatus.put("Mi Negocio menu", "PASS");
	}

	private void executeStepAgregarNegocioModal(final Page appPage) throws IOException {
		setCurrentStep("Agregar Negocio modal");

		clickAndWait(appPage, findVisibleClickable(appPage, AGREGAR_NEGOCIO_TEXT));
		assertTextVisible(appPage, Pattern.compile("(?i)^\\s*Crear\\s+Nuevo\\s+Negocio\\s*$"));
		assertTextVisible(appPage, Pattern.compile("(?i)^\\s*Nombre\\s+del\\s+Negocio\\s*$"));
		assertTextVisible(appPage, Pattern.compile("(?i)^\\s*Tienes\\s+2\\s+de\\s+3\\s+negocios\\s*$"));
		assertTextVisible(appPage, Pattern.compile("(?i)^\\s*Cancelar\\s*$"));
		assertTextVisible(appPage, Pattern.compile("(?i)^\\s*Crear\\s+Negocio\\s*$"));
		takeScreenshot(appPage, "03-agregar-negocio-modal.png", false);

		final Locator nombreNegocioField = locateNombreDelNegocioInput(appPage);
		nombreNegocioField.fill("Negocio Prueba Automatizacion");
		clickAndWait(appPage, findVisibleClickable(appPage, Pattern.compile("(?i)^\\s*Cancelar\\s*$")));

		reportStatus.put("Agregar Negocio modal", "PASS");
	}

	private void executeStepAdministrarNegocios(final Page appPage) throws IOException {
		setCurrentStep("Administrar Negocios view");

		if (!isVisible(appPage.getByText(ADMINISTRAR_NEGOCIOS_TEXT).first(), 1000)) {
			clickAndWait(appPage, findVisibleClickable(appPage, MI_NEGOCIO_TEXT));
		}

		clickAndWait(appPage, findVisibleClickable(appPage, ADMINISTRAR_NEGOCIOS_TEXT));
		assertTextVisible(appPage, Pattern.compile("(?i)^\\s*Informaci[oó]n\\s+General\\s*$"));
		assertTextVisible(appPage, Pattern.compile("(?i)^\\s*Detalles\\s+de\\s+la\\s+Cuenta\\s*$"));
		assertTextVisible(appPage, Pattern.compile("(?i)^\\s*Tus\\s+Negocios\\s*$"));
		assertTextVisible(appPage, Pattern.compile("(?i)^\\s*Secci[oó]n\\s+Legal\\s*$"));
		takeScreenshot(appPage, "04-administrar-negocios-full.png", true);

		reportStatus.put("Administrar Negocios view", "PASS");
	}

	private void executeStepInformacionGeneral(final Page appPage) {
		setCurrentStep("Información General");

		assertTextVisible(appPage, Pattern.compile("(?i)^\\s*Informaci[oó]n\\s+General\\s*$"));
		assertTextVisible(appPage, Pattern.compile("(?i)^\\s*BUSINESS\\s+PLAN\\s*$"));
		assertTextVisible(appPage, Pattern.compile("(?i)^\\s*Cambiar\\s+Plan\\s*$"));

		final Locator emailByKnownAccount = appPage.getByText(Pattern.compile(Pattern.quote(GOOGLE_ACCOUNT_EMAIL))).first();
		if (!isVisible(emailByKnownAccount, 2000)) {
			final Locator emailByPattern = appPage.locator("text=/" + ACCOUNT_EMAIL_PATTERN.pattern() + "/").first();
			Assert.assertTrue("Expected a visible user email.", isVisible(emailByPattern, DEFAULT_TIMEOUT_MS));
		}

		// In addition to the email, ensure there is at least one "Nombre" label/value visible for the profile.
		assertTextVisible(appPage, Pattern.compile("(?i)Nombre"));

		reportStatus.put("Información General", "PASS");
	}

	private void executeStepDetallesCuenta(final Page appPage) {
		setCurrentStep("Detalles de la Cuenta");

		assertTextVisible(appPage, Pattern.compile("(?i)^\\s*Detalles\\s+de\\s+la\\s+Cuenta\\s*$"));
		assertTextVisible(appPage, Pattern.compile("(?i)Cuenta\\s+creada"));
		assertTextVisible(appPage, Pattern.compile("(?i)Estado\\s+activo"));
		assertTextVisible(appPage, Pattern.compile("(?i)Idioma\\s+seleccionado"));

		reportStatus.put("Detalles de la Cuenta", "PASS");
	}

	private void executeStepTusNegocios(final Page appPage) {
		setCurrentStep("Tus Negocios");

		assertTextVisible(appPage, Pattern.compile("(?i)^\\s*Tus\\s+Negocios\\s*$"));
		assertTextVisible(appPage, AGREGAR_NEGOCIO_TEXT);
		assertTextVisible(appPage, Pattern.compile("(?i)^\\s*Tienes\\s+2\\s+de\\s+3\\s+negocios\\s*$"));

		final Locator businessContainer = appPage
				.locator("section:has-text(\"Tus Negocios\"), div:has-text(\"Tus Negocios\")")
				.first();
		Assert.assertTrue("Expected business list container to be visible.", isVisible(businessContainer, DEFAULT_TIMEOUT_MS));

		reportStatus.put("Tus Negocios", "PASS");
	}

	private void executeStepTerminos(final Page appPage) throws IOException {
		setCurrentStep("Términos y Condiciones");

		final LegalResult legalResult = openAndValidateLegalLink(
				appPage,
				Pattern.compile("(?i)^\\s*T[eé]rminos\\s+y\\s+Condiciones\\s*$"),
				Pattern.compile("(?i)^\\s*T[eé]rminos\\s+y\\s+Condiciones\\s*$"),
				"05-terminos-y-condiciones.png");

		terminosUrl = legalResult.finalUrl();
		reportStatus.put("Términos y Condiciones", "PASS");
	}

	private void executeStepPoliticaPrivacidad(final Page appPage) throws IOException {
		setCurrentStep("Política de Privacidad");

		final LegalResult legalResult = openAndValidateLegalLink(
				appPage,
				Pattern.compile("(?i)^\\s*Pol[ií]tica\\s+de\\s+Privacidad\\s*$"),
				Pattern.compile("(?i)^\\s*Pol[ií]tica\\s+de\\s+Privacidad\\s*$"),
				"06-politica-de-privacidad.png");

		politicaUrl = legalResult.finalUrl();
		reportStatus.put("Política de Privacidad", "PASS");
	}

	private LegalResult openAndValidateLegalLink(
			final Page appPage,
			final Pattern linkText,
			final Pattern expectedHeading,
			final String screenshotName) throws IOException {

		final String applicationUrlBefore = appPage.url();
		final Locator legalLink = findVisibleClickable(appPage, linkText);
		final Page legalPage = clickAndGetNewTabOrCurrent(appPage, legalLink);

		assertTextVisible(legalPage, expectedHeading);
		final String bodyText = legalPage.locator("body").innerText();
		Assert.assertTrue("Expected visible legal content text.", bodyText != null && bodyText.trim().length() > 120);
		takeScreenshot(legalPage, screenshotName, true);

		final boolean openedInNewTab = !Objects.equals(legalPage, appPage);
		final String finalUrl = legalPage.url();

		if (openedInNewTab) {
			legalPage.close();
			appPage.bringToFront();
		} else if (!Objects.equals(appPage.url(), applicationUrlBefore)) {
			final Response backResponse = appPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			if (backResponse == null) {
				appPage.navigate(applicationUrlBefore, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			}
			waitForUi(appPage);
		}

		return new LegalResult(finalUrl);
	}

	private Page clickAndGetNewTabOrCurrent(final Page page, final Locator locator) {
		try {
			final Page popup = page.waitForPopup(() -> locator.click(), new Page.WaitForPopupOptions().setTimeout(7000));
			waitForUi(popup);
			return popup;
		} catch (PlaywrightException popupNotOpened) {
			locator.click();
			waitForUi(page);
			return page;
		}
	}

	private void selectGoogleAccountIfVisible(final Page googlePage) {
		final Locator accountByText = googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, new Page.GetByTextOptions().setExact(true)).first();
		if (isVisible(accountByText, 5000)) {
			accountByText.click();
			waitForUi(googlePage);
			return;
		}

		final Locator accountByRole = googlePage
				.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(GOOGLE_ACCOUNT_EMAIL))))
				.first();
		if (isVisible(accountByRole, 3000)) {
			accountByRole.click();
			waitForUi(googlePage);
		}
	}

	private void waitForSidebar(final Page appPage) {
		final Locator sidebar = appPage.locator("aside, nav").first();
		final boolean sidebarVisible = isVisible(sidebar, DEFAULT_TIMEOUT_MS);
		if (!sidebarVisible) {
			assertTextVisible(appPage, MENU_NEGOCIO_TEXT);
		}
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.scrollIntoViewIfNeeded();
		locator.click();
		waitForUi(page);
	}

	private Locator locateNombreDelNegocioInput(final Page appPage) {
		final Locator byLabel = appPage.getByLabel(Pattern.compile("(?i)^\\s*Nombre\\s+del\\s+Negocio\\s*$")).first();
		if (isVisible(byLabel, 2000)) {
			return byLabel;
		}

		final Locator byPlaceholder = appPage.getByPlaceholder(Pattern.compile("(?i)Nombre\\s+del\\s+Negocio")).first();
		if (isVisible(byPlaceholder, 2000)) {
			return byPlaceholder;
		}

		throw new AssertionError("Input field 'Nombre del Negocio' was not visible.");
	}

	private void assertTextVisible(final Page page, final Pattern textPattern) {
		final Locator locator = page.getByText(textPattern).first();
		locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
	}

	private Locator findVisibleClickable(final Page page, final Pattern textPattern) {
		final Locator[] candidates = new Locator[] {
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern)).first(),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(textPattern)).first(),
				page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(textPattern)).first(),
				page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(textPattern)).first(),
				page.getByText(textPattern).first() };

		for (final Locator candidate : candidates) {
			if (isVisible(candidate, 1200)) {
				return candidate;
			}
		}

		throw new AssertionError("Unable to locate a visible element by text pattern: " + textPattern);
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			return locator.isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMs));
		} catch (PlaywrightException error) {
			return false;
		}
	}

	private void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (PlaywrightException ignored) {
			// Not every screen reaches network idle consistently; DOMContentLoaded already completed.
		}
		page.waitForTimeout(600);
	}

	private void takeScreenshot(final Page page, final String fileName, final boolean fullPage) throws IOException {
		Files.createDirectories(screenshotsDir);
		page.screenshot(new Page.ScreenshotOptions()
				.setPath(screenshotsDir.resolve(fileName))
				.setFullPage(fullPage));
	}

	private void createEvidenceDirectories() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDir = Paths.get("target", "saleads-e2e", timestamp);
		screenshotsDir = evidenceDir.resolve("screenshots");
		Files.createDirectories(screenshotsDir);
	}

	private void writeReportFile() throws IOException {
		Files.createDirectories(evidenceDir);
		final Path reportPath = evidenceDir.resolve("final-report.json");
		final StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"name\": \"saleads_mi_negocio_full_test\",\n");
		json.append("  \"generatedAt\": \"").append(LocalDateTime.now()).append("\",\n");
		json.append("  \"report\": {\n");

		int index = 0;
		for (final Map.Entry<String, String> entry : reportStatus.entrySet()) {
			json.append("    \"").append(escapeJson(entry.getKey())).append("\": \"")
					.append(escapeJson(entry.getValue())).append("\"");
			if (index < reportStatus.size() - 1) {
				json.append(",");
			}
			json.append("\n");
			index++;
		}

		json.append("  },\n");
		json.append("  \"evidence\": {\n");
		json.append("    \"screenshotsDirectory\": \"").append(escapeJson(screenshotsDir.toString())).append("\",\n");
		json.append("    \"terminosYCondicionesUrl\": \"").append(escapeJson(terminosUrl)).append("\",\n");
		json.append("    \"politicaDePrivacidadUrl\": \"").append(escapeJson(politicaUrl)).append("\"\n");
		json.append("  }\n");
		json.append("}\n");

		Files.writeString(reportPath, json.toString());
	}

	private void setCurrentStep(final String stepName) {
		if ("NOT_RUN".equals(reportStatus.get(stepName))) {
			reportStatus.put(stepName, "IN_PROGRESS");
		}
	}

	private void reportCurrentFailure(final Throwable throwable) {
		for (final Map.Entry<String, String> entry : reportStatus.entrySet()) {
			if ("IN_PROGRESS".equals(entry.getValue())) {
				entry.setValue("FAIL: " + throwable.getClass().getSimpleName() + " - " + throwable.getMessage());
				return;
			}
		}
	}

	private static Map<String, String> createDefaultReportStatus() {
		final Map<String, String> status = new LinkedHashMap<>();
		status.put("Login", "NOT_RUN");
		status.put("Mi Negocio menu", "NOT_RUN");
		status.put("Agregar Negocio modal", "NOT_RUN");
		status.put("Administrar Negocios view", "NOT_RUN");
		status.put("Información General", "NOT_RUN");
		status.put("Detalles de la Cuenta", "NOT_RUN");
		status.put("Tus Negocios", "NOT_RUN");
		status.put("Términos y Condiciones", "NOT_RUN");
		status.put("Política de Privacidad", "NOT_RUN");
		return status;
	}

	private static String resolveConfig(final String propertyName, final String envName, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.trim().isEmpty()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.trim().isEmpty()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	private static String escapeJson(final String value) {
		return value
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r");
	}

	private record LegalResult(String finalUrl) {
	}
}
