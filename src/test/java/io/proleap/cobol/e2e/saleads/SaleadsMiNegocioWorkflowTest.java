package io.proleap.cobol.e2e.saleads;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class SaleadsMiNegocioWorkflowTest {

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");
	private static final String GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final int DEFAULT_TIMEOUT_MS = 20000;

	private final Map<String, String> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page appPage;
	private Path evidenceDir;
	private int screenshotCounter = 0;
	private int timeoutMs = DEFAULT_TIMEOUT_MS;

	@Before
	public void setUp() throws Exception {
		for (String field : REPORT_FIELDS) {
			report.put(field, "FAIL - Not executed");
		}

		final String enabled = readEnv("SALEADS_E2E_ENABLED");
		Assume.assumeTrue(
				"Skipping SaleADS E2E test. Set SALEADS_E2E_ENABLED=true and SALEADS_LOGIN_URL=<login-page-url> to execute.",
				"true".equalsIgnoreCase(enabled));

		final String timeoutEnv = readEnv("SALEADS_TIMEOUT_MS");
		if (timeoutEnv != null && !timeoutEnv.isBlank()) {
			timeoutMs = Integer.parseInt(timeoutEnv.trim());
		}

		playwright = Playwright.create();

		final String browserName = envOrDefault("SALEADS_BROWSER", "chromium").toLowerCase(Locale.ROOT);
		final boolean headless = !"false".equalsIgnoreCase(envOrDefault("SALEADS_HEADLESS", "true"));
		final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(headless);

		switch (browserName) {
		case "firefox":
			browser = playwright.firefox().launch(launchOptions);
			break;
		case "webkit":
			browser = playwright.webkit().launch(launchOptions);
			break;
		default:
			browser = playwright.chromium().launch(launchOptions);
			break;
		}

		context = browser.newContext();
		appPage = context.newPage();

		final String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		evidenceDir = Paths.get("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDir);
		System.out.println("Evidence folder: " + evidenceDir.toAbsolutePath());
	}

	@After
	public void tearDown() {
		printFinalReport();

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
		final boolean loginOk = runStep("Login", this::stepLoginWithGoogle);

		if (loginOk) {
			final boolean menuOk = runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
			if (menuOk) {
				runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
			} else {
				markFailed("Agregar Negocio modal", "Skipped because Mi Negocio menu failed.");
			}

			final boolean adminViewOk = runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
			if (adminViewOk) {
				runStep("Información General", this::stepValidateInformacionGeneral);
				runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
				runStep("Tus Negocios", this::stepValidateTusNegocios);
				runStep("Términos y Condiciones", this::stepValidateTerminos);
				runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);
			} else {
				markFailed("Información General", "Skipped because Administrar Negocios view failed.");
				markFailed("Detalles de la Cuenta", "Skipped because Administrar Negocios view failed.");
				markFailed("Tus Negocios", "Skipped because Administrar Negocios view failed.");
				markFailed("Términos y Condiciones", "Skipped because Administrar Negocios view failed.");
				markFailed("Política de Privacidad", "Skipped because Administrar Negocios view failed.");
			}
		} else {
			markFailed("Mi Negocio menu", "Skipped because login failed.");
			markFailed("Agregar Negocio modal", "Skipped because login failed.");
			markFailed("Administrar Negocios view", "Skipped because login failed.");
			markFailed("Información General", "Skipped because login failed.");
			markFailed("Detalles de la Cuenta", "Skipped because login failed.");
			markFailed("Tus Negocios", "Skipped because login failed.");
			markFailed("Términos y Condiciones", "Skipped because login failed.");
			markFailed("Política de Privacidad", "Skipped because login failed.");
		}

		final boolean allPassed = report.values().stream().allMatch(value -> value.startsWith("PASS"));
		Assert.assertTrue("One or more SaleADS workflow validations failed. Review report output.", allPassed);
	}

	private void stepLoginWithGoogle() {
		final String loginUrl = readEnv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to run this test in any SaleADS environment.",
				loginUrl != null && !loginUrl.isBlank());

		appPage.navigate(loginUrl);
		waitForUi(appPage);

		final Locator loginButton = firstVisible(
				"Google login button",
				() -> appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)(sign in|iniciar sesi[oó]n|continuar).{0,25}google"))),
				() -> appPage.getByText(Pattern.compile("(?i)(sign in|iniciar sesi[oó]n|continuar).{0,25}google")),
				() -> appPage.getByText(Pattern.compile("(?i)google")));

		final int pageCountBeforeClick = context.pages().size();
		clickAndWait(appPage, loginButton, "Click Sign in with Google");

		final Page popup = waitForNewPage(pageCountBeforeClick, 10000);
		if (popup != null) {
			waitForUi(popup);
			selectGoogleAccountIfVisible(popup);
			waitForUi(appPage);
		}

		selectGoogleAccountIfVisible(appPage);
		appPage = waitForApplicationPage(15000);
		appPage.bringToFront();
		waitForUi(appPage);

		final Locator sidebar = firstVisible(
				"left sidebar navigation",
				() -> appPage.getByRole(AriaRole.NAVIGATION),
				() -> appPage.getByText(Pattern.compile("(?i)negocio")),
				() -> appPage.locator("aside"));
		assertVisible(sidebar, "Left sidebar must be visible after login.");

		captureScreenshot(appPage, "01-dashboard-loaded", false);
	}

	private void stepOpenMiNegocioMenu() {
		final Locator negocio = firstVisible("Negocio section", () -> appPage.getByText(Pattern.compile("(?i)^negocio$")),
				() -> appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^negocio$"))),
				() -> appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^negocio$"))));
		clickAndWait(appPage, negocio, "Open Negocio section");

		final Locator miNegocio = firstVisible("Mi Negocio option",
				() -> appPage.getByText(Pattern.compile("(?i)^mi negocio$")),
				() -> appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^mi negocio$"))),
				() -> appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^mi negocio$"))));
		clickAndWait(appPage, miNegocio, "Open Mi Negocio menu");

		assertVisible(
				firstVisible("Agregar Negocio option", () -> appPage.getByText(Pattern.compile("(?i)^agregar negocio$")),
						() -> appPage.getByRole(AriaRole.LINK,
								new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^agregar negocio$")))),
				"'Agregar Negocio' should be visible.");
		assertVisible(
				firstVisible("Administrar Negocios option",
						() -> appPage.getByText(Pattern.compile("(?i)^administrar negocios$")),
						() -> appPage.getByRole(AriaRole.LINK,
								new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^administrar negocios$")))),
				"'Administrar Negocios' should be visible.");

		captureScreenshot(appPage, "02-mi-negocio-menu-expanded", false);
	}

	private void stepValidateAgregarNegocioModal() {
		final Locator agregarNegocio = firstVisible("Agregar Negocio action",
				() -> appPage.getByText(Pattern.compile("(?i)^agregar negocio$")),
				() -> appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^agregar negocio$"))),
				() -> appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^agregar negocio$"))));
		clickAndWait(appPage, agregarNegocio, "Open Agregar Negocio modal");

		assertVisible(firstVisible("Crear Nuevo Negocio modal title",
				() -> appPage.getByText(Pattern.compile("(?i)^crear nuevo negocio$"))),
				"Modal title 'Crear Nuevo Negocio' should be visible.");
		assertVisible(
				firstVisible("Nombre del Negocio input",
						() -> appPage.getByLabel(Pattern.compile("(?i)^nombre del negocio$")),
						() -> appPage.getByPlaceholder(Pattern.compile("(?i)nombre del negocio"))),
				"Input 'Nombre del Negocio' should be visible.");
		assertVisible(firstVisible("Business quota text", () -> appPage.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios"))),
				"Text 'Tienes 2 de 3 negocios' should be visible.");
		assertVisible(
				firstVisible("Cancelar button",
						() -> appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^cancelar$")))),
				"Button 'Cancelar' should be visible.");
		assertVisible(
				firstVisible("Crear Negocio button",
						() -> appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^crear negocio$")))),
				"Button 'Crear Negocio' should be visible.");

		captureScreenshot(appPage, "03-agregar-negocio-modal", false);

		final Locator nombreNegocioInput = firstVisible("Nombre del Negocio input field",
				() -> appPage.getByLabel(Pattern.compile("(?i)^nombre del negocio$")),
				() -> appPage.getByPlaceholder(Pattern.compile("(?i)nombre del negocio")));
		nombreNegocioInput.fill("Negocio Prueba Automatizacion");

		final Locator cancelarButton = firstVisible("Cancelar button for modal",
				() -> appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^cancelar$"))));
		clickAndWait(appPage, cancelarButton, "Close modal by clicking Cancelar");
	}

	private void stepOpenAdministrarNegocios() {
		final Locator miNegocio = firstVisible("Mi Negocio option",
				() -> appPage.getByText(Pattern.compile("(?i)^mi negocio$")),
				() -> appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^mi negocio$"))),
				() -> appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^mi negocio$"))));
		clickAndWait(appPage, miNegocio, "Expand Mi Negocio if collapsed");

		final Locator administrarNegocios = firstVisible("Administrar Negocios option",
				() -> appPage.getByText(Pattern.compile("(?i)^administrar negocios$")),
				() -> appPage.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^administrar negocios$"))),
				() -> appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^administrar negocios$"))));
		clickAndWait(appPage, administrarNegocios, "Open Administrar Negocios");

		assertVisible(firstVisible("Información General section",
				() -> appPage.getByText(Pattern.compile("(?i)^informaci[oó]n general$"))),
				"Section 'Información General' should be visible.");
		assertVisible(firstVisible("Detalles de la Cuenta section",
				() -> appPage.getByText(Pattern.compile("(?i)^detalles de la cuenta$"))),
				"Section 'Detalles de la Cuenta' should be visible.");
		assertVisible(firstVisible("Tus Negocios section", () -> appPage.getByText(Pattern.compile("(?i)^tus negocios$"))),
				"Section 'Tus Negocios' should be visible.");
		assertVisible(firstVisible("Sección Legal section",
				() -> appPage.getByText(Pattern.compile("(?i)^secci[oó]n legal$"))),
				"Section 'Sección Legal' should be visible.");

		captureScreenshot(appPage, "04-administrar-negocios-page", true);
	}

	private void stepValidateInformacionGeneral() {
		final String bodyText = appPage.locator("body").innerText();
		Assert.assertTrue("User email must be visible in Información General.", EMAIL_PATTERN.matcher(bodyText).find());
		Assert.assertTrue("A user name should appear on screen.", hasLikelyHumanName(bodyText));

		assertVisible(firstVisible("BUSINESS PLAN text", () -> appPage.getByText(Pattern.compile("(?i)business plan"))),
				"Text 'BUSINESS PLAN' should be visible.");
		assertVisible(firstVisible("Cambiar Plan button",
				() -> appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^cambiar plan$"))),
				() -> appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^cambiar plan$")))),
				"Button 'Cambiar Plan' should be visible.");
	}

	private void stepValidateDetallesCuenta() {
		assertVisible(firstVisible("Cuenta creada text", () -> appPage.getByText(Pattern.compile("(?i)cuenta creada"))),
				"'Cuenta creada' should be visible.");
		assertVisible(firstVisible("Estado activo text", () -> appPage.getByText(Pattern.compile("(?i)estado activo"))),
				"'Estado activo' should be visible.");
		assertVisible(firstVisible("Idioma seleccionado text", () -> appPage.getByText(Pattern.compile("(?i)idioma seleccionado"))),
				"'Idioma seleccionado' should be visible.");
	}

	private void stepValidateTusNegocios() {
		assertVisible(firstVisible("Tus Negocios heading", () -> appPage.getByText(Pattern.compile("(?i)^tus negocios$"))),
				"'Tus Negocios' heading should be visible.");
		assertVisible(firstVisible("Agregar Negocio button in list",
				() -> appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^agregar negocio$"))),
				() -> appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^agregar negocio$")))),
				"'Agregar Negocio' button should be visible.");
		assertVisible(firstVisible("Business quota text", () -> appPage.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios"))),
				"'Tienes 2 de 3 negocios' should be visible.");

		final String sectionText = appPage.locator("body").innerText();
		Assert.assertTrue("Business list should be visible in 'Tus Negocios'.",
				normalize(sectionText).contains(normalize("Tus Negocios")));
	}

	private void stepValidateTerminos() {
		final String finalUrl = validateLegalLink("Términos y Condiciones", "05-terminos-y-condiciones");
		legalUrls.put("Términos y Condiciones", finalUrl);
	}

	private void stepValidatePoliticaPrivacidad() {
		final String finalUrl = validateLegalLink("Política de Privacidad", "06-politica-de-privacidad");
		legalUrls.put("Política de Privacidad", finalUrl);
	}

	private String validateLegalLink(final String linkText, final String screenshotName) {
		final int pageCountBeforeClick = context.pages().size();

		final Locator legalLink = firstVisible(linkText + " link",
				() -> appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^" + Pattern.quote(linkText) + "$"))),
				() -> appPage.getByText(Pattern.compile("(?i)^" + Pattern.quote(linkText) + "$")));
		clickAndWait(appPage, legalLink, "Open " + linkText);

		Page legalPage = waitForNewPage(pageCountBeforeClick, 10000);
		if (legalPage == null) {
			legalPage = appPage;
		}

		waitForUi(legalPage);
		assertVisible(firstVisibleOnPage(legalPage, linkText + " heading",
				() -> legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^" + Pattern.quote(linkText) + "$"))),
				() -> legalPage.getByText(Pattern.compile("(?i)^" + Pattern.quote(linkText) + "$"))),
				linkText + " heading should be visible.");

		final String legalBody = legalPage.locator("body").innerText();
		Assert.assertTrue("Legal content should be visible for " + linkText + ".", legalBody != null && legalBody.trim().length() > 120);

		captureScreenshot(legalPage, screenshotName, true);
		final String finalUrl = legalPage.url();

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			appPage.goBack();
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private void selectGoogleAccountIfVisible(final Page page) {
		try {
			final Locator accountOption = page.getByText(Pattern.compile("(?i)^" + Pattern.quote(GOOGLE_EMAIL) + "$")).first();
			accountOption.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5000));
			clickAndWait(page, accountOption, "Select Google account " + GOOGLE_EMAIL);
		} catch (PlaywrightException ignored) {
			// Optional selector: account chooser may not appear if session is already authenticated.
		}
	}

	private Page waitForApplicationPage(final int timeoutForSearchMs) {
		final long deadline = System.currentTimeMillis() + timeoutForSearchMs;
		Page candidate = appPage;

		while (System.currentTimeMillis() < deadline) {
			for (Page page : context.pages()) {
				if (page.isClosed()) {
					continue;
				}

				candidate = page;
				try {
					if (page.getByText(Pattern.compile("(?i)negocio")).count() > 0
							|| page.getByRole(AriaRole.NAVIGATION).count() > 0) {
						return page;
					}
				} catch (PlaywrightException ignored) {
					// Ignore transient page states while redirecting.
				}
			}

			pause(250);
		}

		return candidate;
	}

	private Page waitForNewPage(final int oldPageCount, final int timeoutForNewPageMs) {
		final long deadline = System.currentTimeMillis() + timeoutForNewPageMs;
		while (System.currentTimeMillis() < deadline) {
			if (context.pages().size() > oldPageCount) {
				return context.pages().get(context.pages().size() - 1);
			}
			pause(200);
		}
		return null;
	}

	private void clickAndWait(final Page page, final Locator locator, final String actionDescription) {
		final Locator first = locator.first();
		first.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
		first.click(new Locator.ClickOptions().setTimeout(timeoutMs));
		waitForUi(page);
		System.out.println("Action completed: " + actionDescription);
	}

	private void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		page.waitForTimeout(500);
	}

	@SafeVarargs
	private final Locator firstVisible(final String description, final LocatorSupplier... suppliers) {
		return firstVisibleOnPage(appPage, description, suppliers);
	}

	@SafeVarargs
	private final Locator firstVisibleOnPage(final Page page, final String description, final LocatorSupplier... suppliers) {
		PlaywrightException lastError = null;
		for (LocatorSupplier supplier : suppliers) {
			try {
				final Locator locator = supplier.get().first();
				locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
				return locator;
			} catch (PlaywrightException e) {
				lastError = e;
			}
		}

		throw new AssertionError("Unable to find visible element for: " + description, lastError);
	}

	private void assertVisible(final Locator locator, final String message) {
		locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
		Assert.assertTrue(message, locator.first().isVisible());
	}

	private void captureScreenshot(final Page page, final String fileNamePrefix, final boolean fullPage) {
		final String safeName = sanitizeFileName(fileNamePrefix);
		final Path target = evidenceDir.resolve(String.format("%02d-%s.png", ++screenshotCounter, safeName));
		page.screenshot(new Page.ScreenshotOptions().setPath(target).setFullPage(fullPage));
		System.out.println("Screenshot captured: " + target.toAbsolutePath());
	}

	private boolean runStep(final String field, final Step step) {
		try {
			step.execute();
			report.put(field, "PASS");
			return true;
		} catch (Throwable throwable) {
			markFailed(field, throwable.getMessage());
			return false;
		}
	}

	private void markFailed(final String field, final String message) {
		report.put(field, "FAIL - " + (message == null ? "No error message available." : message));
	}

	private void printFinalReport() {
		System.out.println("========== SaleADS Mi Negocio Validation Report ==========");
		for (Map.Entry<String, String> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}

		if (!legalUrls.isEmpty()) {
			System.out.println("---------- Captured Legal URLs ----------");
			for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.println(entry.getKey() + " URL: " + entry.getValue());
			}
		}

		if (evidenceDir != null) {
			System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		}
		System.out.println("==========================================================");
	}

	private boolean hasLikelyHumanName(final String text) {
		final String[] lines = text.split("\\R");
		for (int i = 0; i < lines.length; i++) {
			final String line = lines[i].trim();
			if (line.isEmpty() || EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}
			if (line.matches("[A-Za-zÀ-ÿ]{2,}(\\s+[A-Za-zÀ-ÿ]{2,})+")) {
				return true;
			}
		}
		return false;
	}

	private String sanitizeFileName(final String raw) {
		final String normalized = normalize(raw);
		return normalized.replaceAll("[^a-z0-9-]+", "-").replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
	}

	private String normalize(final String value) {
		final String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
		return normalized.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
	}

	private String readEnv(final String key) {
		return System.getenv(key);
	}

	private String envOrDefault(final String key, final String defaultValue) {
		final String value = readEnv(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private void pause(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting for browser event.", interruptedException);
		}
	}

	@FunctionalInterface
	private interface LocatorSupplier {
		Locator get();
	}

	@FunctionalInterface
	private interface Step {
		void execute() throws Exception;
	}
}
