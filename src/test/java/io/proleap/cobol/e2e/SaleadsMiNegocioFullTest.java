package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
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

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioFullTest {

	private static final long DEFAULT_TIMEOUT_MS = 30000;
	private static final String DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> failures = new LinkedHashMap<>();

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page page;
	private Path artifactsDir;
	private String adminPageUrl;
	private String termsFinalUrl;
	private String privacyFinalUrl;

	@Before
	public void setUp() throws IOException {
		artifactsDir = Files.createDirectories(Paths.get("target", "saleads-mi-negocio-artifacts",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));
		initReport();
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();

		if (context != null) {
			context.close();
		}
		if (browser != null) {
			browser.close();
		}
		if (playwright != null) {
			playwright.close();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final String loginUrl = firstNonBlank(readConfig("SALEADS_LOGIN_URL", "saleads.login.url"),
				readConfig("SALEADS_BASE_URL", "saleads.base.url"));
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL) to execute this E2E workflow in any SaleADS environment.",
				loginUrl != null && !loginUrl.isBlank());

		playwright = Playwright.create();
		final String headfulValue = readConfigOrDefault("SALEADS_HEADFUL", "saleads.headful", "false");
		browser = playwright.chromium()
				.launch(new BrowserType.LaunchOptions().setHeadless(!Boolean.parseBoolean(headfulValue)));
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 900));
		context.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
		page = context.newPage();

		final String accountEmail = readConfigOrDefault("SALEADS_GOOGLE_EMAIL", "saleads.google.email",
				DEFAULT_ACCOUNT_EMAIL);

		runStep("Login", () -> stepLoginWithGoogle(loginUrl, accountEmail));
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			if (!Boolean.TRUE.equals(entry.getValue())) {
				failedSteps.add(entry.getKey());
			}
		}

		assertTrue("Validation failures: " + failedSteps + ". Details: " + failures, failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle(final String loginUrl, final String accountEmail) throws IOException {
		page.navigate(loginUrl);
		waitForUiToSettle(page);

		final Locator googleLoginButton = findByAnyText(page, "Sign in with Google", "Iniciar sesión con Google",
				"Ingresar con Google", "Continuar con Google", "Google");
		assertVisible(googleLoginButton, "Google login button");

		final int pagesBeforeClick = context.pages().size();
		clickAndWait(googleLoginButton);

		Page googlePage = page;
		if (context.pages().size() > pagesBeforeClick) {
			googlePage = context.pages().get(context.pages().size() - 1);
			googlePage.bringToFront();
			waitForUiToSettle(googlePage);
		}

		final Locator accountOption = findByAnyText(googlePage, accountEmail);
		if (accountOption.count() > 0 && accountOption.first().isVisible()) {
			accountOption.first().click();
			waitForUiToSettle(googlePage);
		}

		page.bringToFront();
		waitForUiToSettle(page);
		assertVisible(findByAnyText(page, "Negocio"), "Main app interface");
		assertVisible(findFirstVisible(page, "aside", "nav"), "Left sidebar navigation");
		captureScreenshot(page, "01-dashboard-loaded", true);
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		assertVisible(findFirstVisible(page, "aside", "nav"), "Left sidebar");
		assertVisible(findByAnyText(page, "Negocio"), "Negocio section");

		clickAndWait(findByAnyText(page, "Mi Negocio"));
		assertVisible(findByAnyText(page, "Agregar Negocio"), "Agregar Negocio submenu");
		assertVisible(findByAnyText(page, "Administrar Negocios"), "Administrar Negocios submenu");
		captureScreenshot(page, "02-mi-negocio-menu-expanded", true);
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickAndWait(findByAnyText(page, "Agregar Negocio"));

		assertVisible(findByAnyText(page, "Crear Nuevo Negocio"), "Modal title Crear Nuevo Negocio");
		assertVisible(findByAnyText(page, "Nombre del Negocio"), "Nombre del Negocio input label");
		assertVisible(findByAnyText(page, "Tienes 2 de 3 negocios"), "Business count text");
		assertVisible(findByAnyText(page, "Cancelar"), "Cancelar button");
		assertVisible(findByAnyText(page, "Crear Negocio"), "Crear Negocio button");
		captureScreenshot(page, "03-agregar-negocio-modal", true);

		final Locator nameInput = findFirstVisible(page, "input[placeholder*='Negocio']", "input[name*='negocio']",
				"input[type='text']");
		if (nameInput.count() > 0) {
			nameInput.first().click();
			nameInput.first().fill("Negocio Prueba Automatizacion");
		}

		clickAndWait(findByAnyText(page, "Cancelar"));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		final Locator administrarNegocios = findByAnyText(page, "Administrar Negocios");
		if (administrarNegocios.count() == 0 || !administrarNegocios.first().isVisible()) {
			clickAndWait(findByAnyText(page, "Mi Negocio"));
		}

		clickAndWait(findByAnyText(page, "Administrar Negocios"));
		waitForUiToSettle(page);
		adminPageUrl = page.url();

		assertVisible(findByAnyText(page, "Información General"), "Información General section");
		assertVisible(findByAnyText(page, "Detalles de la Cuenta"), "Detalles de la Cuenta section");
		assertVisible(findByAnyText(page, "Tus Negocios"), "Tus Negocios section");
		assertVisible(findByAnyText(page, "Sección Legal"), "Sección Legal section");
		captureScreenshot(page, "04-administrar-negocios-view", true);
	}

	private void stepValidateInformacionGeneral() {
		assertVisible(findByAnyText(page, "Información General"), "Información General heading");
		assertVisible(findByAnyText(page, "BUSINESS PLAN"), "BUSINESS PLAN text");
		assertVisible(findByAnyText(page, "Cambiar Plan"), "Cambiar Plan button");

		final Locator emailLocator = page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").first();
		assertVisible(emailLocator, "User email");

		final String expectedUserName = readConfig("SALEADS_EXPECTED_USER_NAME", "saleads.expected.user.name");
		if (expectedUserName != null && !expectedUserName.isBlank()) {
			assertVisible(findByAnyText(page, expectedUserName), "User name");
		} else {
			assertTrue("Could not infer user name. Set SALEADS_EXPECTED_USER_NAME for strict validation.",
					hasLikelyUserNameInPage());
		}
	}

	private void stepValidateDetallesCuenta() {
		assertVisible(findByAnyText(page, "Detalles de la Cuenta"), "Detalles de la Cuenta heading");
		assertVisible(findByAnyText(page, "Cuenta creada"), "Cuenta creada label");
		assertVisible(findByAnyText(page, "Estado activo"), "Estado activo label");
		assertVisible(findByAnyText(page, "Idioma seleccionado"), "Idioma seleccionado label");
	}

	private void stepValidateTusNegocios() {
		assertVisible(findByAnyText(page, "Tus Negocios"), "Tus Negocios heading");
		assertVisible(findByAnyText(page, "Agregar Negocio"), "Agregar Negocio button");
		assertVisible(findByAnyText(page, "Tienes 2 de 3 negocios"), "Business count text in Tus Negocios");

		final Locator businessItems = page.locator("main li, main [role='listitem'], main table tbody tr");
		assertTrue("Business list is not visible.", businessItems.count() > 0);
	}

	private void stepValidateTerminosCondiciones() throws IOException {
		termsFinalUrl = validateLegalLink("Términos y Condiciones", "Terminos y Condiciones",
				"05-terminos-y-condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		privacyFinalUrl = validateLegalLink("Política de Privacidad", "Politica de Privacidad",
				"06-politica-de-privacidad");
	}

	private String validateLegalLink(final String linkPrimaryText, final String linkFallbackText, final String screenshotName)
			throws IOException {
		final Locator legalLink = findByAnyText(page, linkPrimaryText, linkFallbackText);
		assertVisible(legalLink, "Legal link " + linkPrimaryText);

		final int pagesBeforeClick = context.pages().size();
		clickAndWait(legalLink);

		Page legalPage = page;
		if (context.pages().size() > pagesBeforeClick) {
			legalPage = context.pages().get(context.pages().size() - 1);
			legalPage.bringToFront();
			waitForUiToSettle(legalPage);
		}

		assertVisible(findByAnyText(legalPage, linkPrimaryText, linkFallbackText), "Legal heading " + linkPrimaryText);
		final String legalContent = legalPage.locator("body").innerText();
		assertTrue("Legal content text is not visible for " + linkPrimaryText,
				legalContent != null && legalContent.trim().length() > 150);
		captureScreenshot(legalPage, screenshotName, true);

		final String finalUrl = legalPage.url();

		if (legalPage != page) {
			legalPage.close();
			page.bringToFront();
		} else if (adminPageUrl != null && !adminPageUrl.equals(page.url())) {
			page.navigate(adminPageUrl);
		}
		waitForUiToSettle(page);
		return finalUrl;
	}

	private boolean hasLikelyUserNameInPage() {
		final List<String> texts = page.locator("main h1, main h2, main h3, main p, main span, main strong").allInnerTexts();
		for (final String rawText : texts) {
			final String text = rawText == null ? "" : rawText.trim();
			if (text.isEmpty()) {
				continue;
			}
			final boolean looksLikeName = text.matches("[A-Za-zÀ-ÿ]{2,}(\\s+[A-Za-zÀ-ÿ]{2,}){1,3}");
			final boolean isKnownLabel = text.equals("Información General") || text.equals("BUSINESS PLAN")
					|| text.equals("Cambiar Plan") || text.equals("Detalles de la Cuenta") || text.equals("Tus Negocios")
					|| text.equals("Sección Legal");
			if (looksLikeName && !isKnownLabel) {
				return true;
			}
		}
		return false;
	}

	private void runStep(final String stepName, final ThrowingRunnable action) {
		try {
			action.run();
			report.put(stepName, true);
		} catch (final Throwable throwable) {
			report.put(stepName, false);
			failures.put(stepName, throwable.getMessage());
			try {
				if (page != null) {
					captureScreenshot(page, "failed-" + slugify(stepName), true);
				}
			} catch (final IOException ignored) {
				// Ignore screenshot failures on already failing steps.
			}
		}
	}

	private void initReport() {
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);
	}

	private String readConfig(final String envKey, final String propertyKey) {
		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}

		final String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}

		return null;
	}

	private String readConfigOrDefault(final String envKey, final String propertyKey, final String defaultValue) {
		final String value = readConfig(envKey, propertyKey);
		return value == null ? defaultValue : value;
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private void waitForUiToSettle(final Page currentPage) {
		try {
			currentPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final PlaywrightException ignored) {
			// Not every SPA navigation triggers a load-state transition.
		}

		try {
			currentPage.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8000));
		} catch (final PlaywrightException ignored) {
			// Network idle is best effort in SPA screens.
		}

		currentPage.waitForTimeout(1000);
	}

	private void clickAndWait(final Locator locator) {
		assertVisible(locator, "Click target");
		locator.first().click();
		waitForUiToSettle(page);
	}

	private void assertVisible(final Locator locator, final String description) {
		assertTrue("Expected visible element for: " + description, locator.count() > 0);
		locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
	}

	private Locator findByAnyText(final Page targetPage, final String... texts) {
		for (final String text : texts) {
			final Locator exact = targetPage.getByText(text, new Page.GetByTextOptions().setExact(true));
			if (exact.count() > 0) {
				return exact.first();
			}

			final Locator partial = targetPage.getByText(Pattern.compile("(?i)" + Pattern.quote(text)));
			if (partial.count() > 0) {
				return partial.first();
			}
		}

		return targetPage.locator("text=/THIS_TEXT_SHOULD_NOT_EXIST/");
	}

	private Locator findFirstVisible(final Page targetPage, final String... selectors) {
		for (final String selector : selectors) {
			final Locator locator = targetPage.locator(selector);
			if (locator.count() > 0) {
				return locator.first();
			}
		}

		return targetPage.locator("text=/THIS_SELECTOR_SHOULD_NOT_EXIST/");
	}

	private void captureScreenshot(final Page targetPage, final String fileName, final boolean fullPage) throws IOException {
		final Path outputPath = artifactsDir.resolve(fileName + ".png");
		targetPage.screenshot(
				new Page.ScreenshotOptions().setFullPage(fullPage).setPath(outputPath).setTimeout(DEFAULT_TIMEOUT_MS));
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder reportContent = new StringBuilder();
		reportContent.append("{\n");
		reportContent.append("  \"name\": \"saleads_mi_negocio_full_test\",\n");
		reportContent.append("  \"status\": {\n");

		int index = 0;
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			reportContent.append("    \"").append(entry.getKey()).append("\": \"")
					.append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL").append("\"");
			if (index < report.size() - 1) {
				reportContent.append(",");
			}
			reportContent.append("\n");
			index++;
		}

		reportContent.append("  },\n");
		reportContent.append("  \"evidence\": {\n");
		reportContent.append("    \"artifactsDir\": \"").append(artifactsDir.toString().replace("\\", "\\\\")).append("\",\n");
		reportContent.append("    \"terminosUrl\": \"").append(valueOrEmpty(termsFinalUrl)).append("\",\n");
		reportContent.append("    \"politicaPrivacidadUrl\": \"").append(valueOrEmpty(privacyFinalUrl)).append("\"\n");
		reportContent.append("  },\n");
		reportContent.append("  \"failures\": ").append(formatFailures()).append("\n");
		reportContent.append("}\n");

		final Path reportFile = artifactsDir.resolve("final-report.json");
		Files.writeString(reportFile, reportContent.toString());
		System.out.println(reportContent);
	}

	private String formatFailures() {
		if (failures.isEmpty()) {
			return "{}";
		}

		final List<String> entries = new ArrayList<>();
		for (final Map.Entry<String, String> entry : failures.entrySet()) {
			final String escapedMessage = entry.getValue() == null ? ""
					: entry.getValue().replace("\\", "\\\\").replace("\"", "\\\"");
			entries.add("\"" + entry.getKey() + "\":\"" + escapedMessage + "\"");
		}
		return "{" + String.join(",", entries) + "}";
	}

	private String valueOrEmpty(final String value) {
		return value == null ? "" : value;
	}

	private String slugify(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
