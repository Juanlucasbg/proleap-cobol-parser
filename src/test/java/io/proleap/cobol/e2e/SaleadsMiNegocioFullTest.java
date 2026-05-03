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
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final String ENABLE_FLAG = "SALEADS_E2E_ENABLED";
	private static final String LOGIN_URL_ENV = "SALEADS_LOGIN_URL";
	private static final String HEADLESS_ENV = "SALEADS_HEADLESS";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> notes = new LinkedHashMap<>();

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page page;
	private Path evidenceDir;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		Assume.assumeTrue("Enable this E2E test with SALEADS_E2E_ENABLED=true.", envBool(ENABLE_FLAG, false));
		final String loginUrl = System.getenv(LOGIN_URL_ENV);
		Assume.assumeTrue(LOGIN_URL_ENV + " must be provided.", loginUrl != null && !loginUrl.isBlank());

		try {
			setup(loginUrl);

			runStep("Login", this::stepLoginWithGoogle);
			runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
			runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
			runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
			runStep("Informacion General", this::stepValidateInformacionGeneral);
			runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
			runStep("Tus Negocios", this::stepValidateTusNegocios);
			runStep("Terminos y Condiciones", this::stepValidateTerminos);
			runStep("Politica de Privacidad", this::stepValidatePrivacidad);

			writeFinalReport();

			Assert.assertTrue("One or more SaleADS Mi Negocio validations failed. See final report in " + evidenceDir,
					report.values().stream().allMatch(Boolean::booleanValue));
		} finally {
			teardown();
		}
	}

	private void setup(final String loginUrl) throws IOException {
		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDir = Path.of("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDir);

		playwright = Playwright.create();
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(envBool(HEADLESS_ENV, true)));
		context = browser.newContext();
		page = context.newPage();
		page.navigate(loginUrl);
		waitForUiToLoad(page);
	}

	private void teardown() {
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

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, true);
			notes.put(stepName, "PASS");
		} catch (final Throwable t) {
			report.put(stepName, false);
			notes.put(stepName, sanitize(t.getMessage()));
			safeScreenshot(page, "FAILED-" + slug(stepName) + ".png", true);
		}
	}

	private void stepLoginWithGoogle() {
		Page authPage = page;
		try {
			authPage = page.waitForPopup(() -> clickByVisibleText(page, Pattern.compile("(?i)(sign in with google|inicia sesion con google|google)")));
		} catch (final PlaywrightException ignored) {
			clickByVisibleText(page, Pattern.compile("(?i)(sign in with google|inicia sesion con google|google)"));
		}

		waitForUiToLoad(authPage);

		if (isVisibleText(authPage, Pattern.compile("(?i)" + Pattern.quote(GOOGLE_ACCOUNT_EMAIL)))) {
			clickByVisibleText(authPage, Pattern.compile("(?i)" + Pattern.quote(GOOGLE_ACCOUNT_EMAIL)));
			waitForUiToLoad(authPage);
		}

		waitForUiToLoad(page);
		waitUntilVisible(page, Pattern.compile("(?i)Mi Negocio|Negocio"), 60_000);
		Assert.assertTrue("Main app interface not detected.", page.locator("main, [role='main']").count() > 0);
		Assert.assertTrue("Left sidebar navigation is not visible.", hasVisibleElement(page.locator("aside, nav")));

		safeScreenshot(page, "01-dashboard.png", true);
	}

	private void stepOpenMiNegocioMenu() {
		Assert.assertTrue("'Negocio' section should be visible.", isVisibleText(page, Pattern.compile("(?i)^\\s*Negocio\\s*$")));
		clickIfVisible(page, Pattern.compile("(?i)^\\s*Negocio\\s*$"));
		clickByVisibleText(page, Pattern.compile("(?i)^\\s*Mi Negocio\\s*$"));
		waitForUiToLoad(page);

		Assert.assertTrue("'Agregar Negocio' is not visible.", isVisibleText(page, Pattern.compile("(?i)^\\s*Agregar Negocio\\s*$")));
		Assert.assertTrue("'Administrar Negocios' is not visible.", isVisibleText(page, Pattern.compile("(?i)^\\s*Administrar Negocios\\s*$")));

		safeScreenshot(page, "02-mi-negocio-menu-expanded.png", true);
	}

	private void stepValidateAgregarNegocioModal() {
		clickByVisibleText(page, Pattern.compile("(?i)^\\s*Agregar Negocio\\s*$"));
		waitUntilVisible(page, Pattern.compile("(?i)^\\s*Crear Nuevo Negocio\\s*$"), 15_000);

		Assert.assertTrue("Modal title 'Crear Nuevo Negocio' is missing.", isVisibleText(page, Pattern.compile("(?i)^\\s*Crear Nuevo Negocio\\s*$")));
		Assert.assertTrue("Input field label 'Nombre del Negocio' is missing.", isVisibleText(page, Pattern.compile("(?i)Nombre del Negocio")));
		Assert.assertTrue("Text 'Tienes 2 de 3 negocios' is missing.", isVisibleText(page, Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")));
		Assert.assertTrue("Button 'Cancelar' is missing.", isVisibleText(page, Pattern.compile("(?i)^\\s*Cancelar\\s*$")));
		Assert.assertTrue("Button 'Crear Negocio' is missing.", isVisibleText(page, Pattern.compile("(?i)^\\s*Crear Negocio\\s*$")));

		fillBusinessNameIfPresent("Negocio Prueba Automatizacion");
		safeScreenshot(page, "03-agregar-negocio-modal.png", true);
		clickByVisibleText(page, Pattern.compile("(?i)^\\s*Cancelar\\s*$"));
		waitForUiToLoad(page);
	}

	private void stepOpenAdministrarNegocios() {
		if (!isVisibleText(page, Pattern.compile("(?i)^\\s*Administrar Negocios\\s*$"))) {
			clickByVisibleText(page, Pattern.compile("(?i)^\\s*Mi Negocio\\s*$"));
			waitForUiToLoad(page);
		}

		clickByVisibleText(page, Pattern.compile("(?i)^\\s*Administrar Negocios\\s*$"));
		waitForUiToLoad(page);

		Assert.assertTrue("'Informacion General' section is missing.", isVisibleText(page, Pattern.compile("(?i)Informaci[oó]n General")));
		Assert.assertTrue("'Detalles de la Cuenta' section is missing.", isVisibleText(page, Pattern.compile("(?i)Detalles de la Cuenta")));
		Assert.assertTrue("'Tus Negocios' section is missing.", isVisibleText(page, Pattern.compile("(?i)Tus Negocios")));
		Assert.assertTrue("'Seccion Legal' section is missing.", isVisibleText(page, Pattern.compile("(?i)Secci[oó]n Legal")));

		safeScreenshot(page, "04-administrar-negocios-full.png", true);
	}

	private void stepValidateInformacionGeneral() {
		final String pageText = page.locator("body").innerText();
		final Matcher emailMatcher = EMAIL_PATTERN.matcher(pageText);

		Assert.assertTrue("Could not detect user email in account view.", emailMatcher.find());
		Assert.assertTrue("Could not detect likely user name in account view.", isVisibleText(page, Pattern.compile("(?i)Nombre|Usuario|Perfil")));
		Assert.assertTrue("'BUSINESS PLAN' text is missing.", isVisibleText(page, Pattern.compile("(?i)BUSINESS\\s+PLAN")));
		Assert.assertTrue("'Cambiar Plan' button is missing.", isVisibleText(page, Pattern.compile("(?i)^\\s*Cambiar Plan\\s*$")));
	}

	private void stepValidateDetallesCuenta() {
		Assert.assertTrue("'Cuenta creada' text is missing.", isVisibleText(page, Pattern.compile("(?i)Cuenta creada")));
		Assert.assertTrue("'Estado activo' text is missing.", isVisibleText(page, Pattern.compile("(?i)Estado activo")));
		Assert.assertTrue("'Idioma seleccionado' text is missing.", isVisibleText(page, Pattern.compile("(?i)Idioma seleccionado")));
	}

	private void stepValidateTusNegocios() {
		Assert.assertTrue("'Tus Negocios' section is missing.", isVisibleText(page, Pattern.compile("(?i)Tus Negocios")));
		Assert.assertTrue("'Agregar Negocio' button is missing in business section.", isVisibleText(page, Pattern.compile("(?i)^\\s*Agregar Negocio\\s*$")));
		Assert.assertTrue("'Tienes 2 de 3 negocios' text is missing.", isVisibleText(page, Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")));
		Assert.assertTrue("Could not detect business list/content in page.", hasBusinessContent());
	}

	private void stepValidateTerminos() {
		validateLegalLink(
				Pattern.compile("(?i)T[eé]rminos\\s+y\\s+Condiciones"),
				Pattern.compile("(?i)T[eé]rminos\\s+y\\s+Condiciones"),
				"08-terminos-y-condiciones.png",
				"Terminos y Condiciones");
	}

	private void stepValidatePrivacidad() {
		validateLegalLink(
				Pattern.compile("(?i)Pol[ií]tica\\s+de\\s+Privacidad"),
				Pattern.compile("(?i)Pol[ií]tica\\s+de\\s+Privacidad"),
				"09-politica-de-privacidad.png",
				"Politica de Privacidad");
	}

	private void validateLegalLink(final Pattern linkPattern, final Pattern headingPattern, final String screenshotName, final String label) {
		Assert.assertTrue("Legal link is not visible for " + label + ".", isVisibleText(page, linkPattern));

		final String appUrlBefore = page.url();
		Page legalPage = page;
		boolean openedInPopup = false;

		try {
			legalPage = page.waitForPopup(() -> clickByVisibleText(page, linkPattern));
			openedInPopup = true;
		} catch (final PlaywrightException ignored) {
			clickByVisibleText(page, linkPattern);
		}

		waitForUiToLoad(legalPage);
		waitUntilVisible(legalPage, headingPattern, 20_000);
		Assert.assertTrue("Heading missing for " + label + ".", isVisibleText(legalPage, headingPattern));
		Assert.assertTrue("Legal content is not visible for " + label + ".", hasReadableLegalContent(legalPage));

		safeScreenshot(legalPage, screenshotName, true);
		notes.put(label + " URL", legalPage.url());

		if (openedInPopup) {
			legalPage.close();
			page.bringToFront();
		} else if (!page.url().equals(appUrlBefore)) {
			page.goBack();
			waitForUiToLoad(page);
		}
	}

	private void fillBusinessNameIfPresent(final String businessName) {
		final List<Locator> candidates = Arrays.asList(
				page.getByLabel(Pattern.compile("(?i)Nombre del Negocio")),
				page.getByPlaceholder(Pattern.compile("(?i)Nombre del Negocio")),
				page.locator("input[name*='nombre'], input[id*='nombre']"));

		for (final Locator candidate : candidates) {
			final int count = candidate.count();
			for (int i = 0; i < count; i++) {
				final Locator field = candidate.nth(i);
				if (safeVisible(field)) {
					field.click();
					field.fill(businessName);
					waitForUiToLoad(page);
					return;
				}
			}
		}
	}

	private void clickByVisibleText(final Page targetPage, final Pattern pattern) {
		final Locator locator = findVisibleClickable(targetPage, pattern);
		locator.scrollIntoViewIfNeeded();
		locator.click();
		waitForUiToLoad(targetPage);
	}

	private void clickIfVisible(final Page targetPage, final Pattern pattern) {
		try {
			final Locator locator = findVisibleClickable(targetPage, pattern);
			locator.scrollIntoViewIfNeeded();
			locator.click();
			waitForUiToLoad(targetPage);
		} catch (final AssertionError ignored) {
			// Some environments may already have this submenu expanded.
		}
	}

	private Locator findVisibleClickable(final Page targetPage, final Pattern pattern) {
		final List<Locator> locators = Arrays.asList(
				targetPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern)),
				targetPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern)),
				targetPage.getByText(pattern));

		for (final Locator locator : locators) {
			final int count = locator.count();
			for (int i = 0; i < count; i++) {
				final Locator candidate = locator.nth(i);
				if (safeVisible(candidate)) {
					return candidate;
				}
			}
		}

		throw new AssertionError("Could not find visible element with text pattern: " + pattern);
	}

	private boolean isVisibleText(final Page targetPage, final Pattern pattern) {
		final Locator locator = targetPage.getByText(pattern);
		final int count = locator.count();
		for (int i = 0; i < count; i++) {
			if (safeVisible(locator.nth(i))) {
				return true;
			}
		}
		return false;
	}

	private boolean hasVisibleElement(final Locator locator) {
		final int count = locator.count();
		for (int i = 0; i < count; i++) {
			if (safeVisible(locator.nth(i))) {
				return true;
			}
		}
		return false;
	}

	private boolean hasBusinessContent() {
		final Locator businessRows = page.locator("section:has-text('Tus Negocios') li, section:has-text('Tus Negocios') [role='listitem'], section:has-text('Tus Negocios') tr");
		if (businessRows.count() > 0) {
			return true;
		}

		final String bodyText = page.locator("body").innerText();
		return bodyText != null && bodyText.replaceAll("\\s+", " ").contains("Tus Negocios");
	}

	private boolean hasReadableLegalContent(final Page targetPage) {
		final String bodyText = targetPage.locator("body").innerText();
		return bodyText != null && bodyText.replaceAll("\\s+", " ").length() >= 250;
	}

	private boolean safeVisible(final Locator locator) {
		try {
			return locator.isVisible();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void waitUntilVisible(final Page targetPage, final Pattern pattern, final double timeoutMs) {
		targetPage.waitForCondition(() -> isVisibleText(targetPage, pattern), new Page.WaitForConditionOptions().setTimeout(timeoutMs));
	}

	private void waitForUiToLoad(final Page targetPage) {
		targetPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			targetPage.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5_000));
		} catch (final PlaywrightException ignored) {
			// Not all pages reach network idle reliably.
		}
		targetPage.waitForTimeout(700);
	}

	private void safeScreenshot(final Page targetPage, final String fileName, final boolean fullPage) {
		try {
			targetPage.screenshot(new Page.ScreenshotOptions()
					.setPath(evidenceDir.resolve(fileName))
					.setFullPage(fullPage));
		} catch (final PlaywrightException ignored) {
			// Evidence best-effort in unstable environments.
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio Workflow Final Report\n");
		sb.append("=======================================\n\n");

		final List<String> orderedFields = Arrays.asList(
				"Login",
				"Mi Negocio menu",
				"Agregar Negocio modal",
				"Administrar Negocios view",
				"Informacion General",
				"Detalles de la Cuenta",
				"Tus Negocios",
				"Terminos y Condiciones",
				"Politica de Privacidad");

		for (final String field : orderedFields) {
			sb.append(String.format("%s: %s%n", field, report.getOrDefault(field, false) ? "PASS" : "FAIL"));
			final String note = notes.get(field);
			if (note != null && !"PASS".equals(note)) {
				sb.append("  Detail: ").append(note).append('\n');
			}
		}

		sb.append('\n').append("Captured URLs\n");
		sb.append("-------------\n");
		appendUrlIfPresent(sb, "Terminos y Condiciones URL");
		appendUrlIfPresent(sb, "Politica de Privacidad URL");

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, sb.toString(), StandardCharsets.UTF_8);
		System.out.println(sb);
		System.out.println("Evidence folder: " + evidenceDir.toAbsolutePath());
	}

	private void appendUrlIfPresent(final StringBuilder sb, final String key) {
		final String value = notes.get(key);
		if (value != null && !value.isBlank()) {
			sb.append(key).append(": ").append(value).append('\n');
		}
	}

	private String sanitize(final String message) {
		if (message == null || message.isBlank()) {
			return "Unknown error";
		}
		return message.replaceAll("[\\r\\n]+", " ").trim();
	}

	private String slug(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private boolean envBool(final String key, final boolean defaultValue) {
		final String value = System.getenv(key);
		return value == null ? defaultValue : Boolean.parseBoolean(value);
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
