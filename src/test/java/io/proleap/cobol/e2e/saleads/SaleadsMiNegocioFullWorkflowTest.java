package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Browser.NewContextOptions;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String GOOGLE_EMAIL_DEFAULT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> stepErrors = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page appPage;
	private Path evidenceDir;
	private int screenshotCounter = 1;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String loginUrl = readConfig("SALEADS_LOGIN_URL", "saleads.login.url", "");
		final String googleEmail = readConfig("SALEADS_GOOGLE_EMAIL", "saleads.google.email", GOOGLE_EMAIL_DEFAULT);
		final String expectedUserName = readConfig("SALEADS_EXPECTED_USER_NAME", "saleads.expected.user.name", "");

		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL or -Dsaleads.login.url to the SaleADS login page for the current environment.",
				!loginUrl.isBlank());

		initializeReport();
		prepareBrowser();
		prepareEvidenceDirectory();

		appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
		waitForUiLoad(appPage);

		runStep("Login", () -> loginWithGoogle(googleEmail));
		runStep("Mi Negocio menu", this::openMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegocios);
		runStep("Información General", () -> validateInformacionGeneral(googleEmail, expectedUserName));
		runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", () -> openAndValidateLegalPage("Términos y Condiciones"));
		runStep("Política de Privacidad", () -> openAndValidateLegalPage("Política de Privacidad"));

		printFinalReport();

		final List<String> failed = report.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey)
				.collect(Collectors.toList());
		assertTrue("One or more workflow validations failed: " + failed + ". Check report output for details.", failed.isEmpty());
	}

	@After
	public void closeBrowser() {
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

	private void initializeReport() {
		report.clear();
		stepErrors.clear();
		legalUrls.clear();

		for (final String field : Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
				"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
				"Términos y Condiciones", "Política de Privacidad")) {
			report.put(field, false);
		}
	}

	private void prepareBrowser() {
		final boolean headless = Boolean
				.parseBoolean(readConfig("SALEADS_HEADLESS", "saleads.headless", Boolean.TRUE.toString()));

		playwright = Playwright.create();
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		context = browser.newContext(new NewContextOptions().setViewportSize(1600, 1200));
		appPage = context.newPage();
		appPage.setDefaultTimeout(20_000);
	}

	private void prepareEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		evidenceDir = Paths.get("target", "evidence", "saleads-mi-negocio", timestamp);
		Files.createDirectories(evidenceDir);
	}

	private void loginWithGoogle(final String googleEmail) {
		final Pattern loginPattern = Pattern.compile(
				"(?i)(sign in with google|iniciar sesi[oó]n con google|continuar con google|ingresar con google|google)");
		final Locator loginButton = requireVisible(findVisibleByName(appPage, loginPattern),
				"No se encontró el botón de inicio de sesión con Google.");

		final int pagesBeforeClick = context.pages().size();
		loginButton.click();
		waitForUiLoad(appPage);

		selectGoogleAccountIfPrompted(googleEmail, pagesBeforeClick);

		waitForVisibleText(appPage, Pattern.compile("(?i)Negocio"), 90_000);
		assertTrue("No se detectó la navegación lateral izquierda tras login.",
				hasVisible(appPage.locator("aside")) || hasVisible(appPage.getByRole(AriaRole.NAVIGATION)));

		captureScreenshot(appPage, "01_dashboard_loaded", false);
	}

	private void selectGoogleAccountIfPrompted(final String googleEmail, final int pagesBeforeClick) {
		Page authPage = null;

		if (context.pages().size() > pagesBeforeClick) {
			authPage = context.pages().get(context.pages().size() - 1);
		} else if (appPage.url().contains("accounts.google.com")) {
			authPage = appPage;
		}

		if (authPage == null) {
			return;
		}

		waitForUiLoad(authPage);
		final Pattern accountPattern = Pattern.compile("(?i)" + Pattern.quote(googleEmail));
		final Locator accountLocator = findVisibleByName(authPage, accountPattern);

		if (accountLocator != null) {
			accountLocator.click();
			waitForUiLoad(authPage);
		}
	}

	private void openMiNegocioMenu() {
		clickByName(appPage, Pattern.compile("(?i)^Negocio$"));
		clickByName(appPage, Pattern.compile("(?i)^Mi Negocio$"));

		waitForVisibleText(appPage, Pattern.compile("(?i)^Agregar Negocio$"), 20_000);
		waitForVisibleText(appPage, Pattern.compile("(?i)^Administrar Negocios$"), 20_000);
		captureScreenshot(appPage, "02_mi_negocio_menu_expanded", false);
	}

	private void validateAgregarNegocioModal() {
		clickByName(appPage, Pattern.compile("(?i)^Agregar Negocio$"));
		waitForVisibleText(appPage, Pattern.compile("(?i)^Crear Nuevo Negocio$"), 20_000);

		waitForVisibleText(appPage, Pattern.compile("(?i)^Nombre del Negocio$"), 20_000);
		waitForVisibleText(appPage, Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios"), 20_000);
		waitForVisibleText(appPage, Pattern.compile("(?i)^Cancelar$"), 20_000);
		waitForVisibleText(appPage, Pattern.compile("(?i)^Crear Negocio$"), 20_000);

		captureScreenshot(appPage, "03_agregar_negocio_modal", false);

		final Locator input = firstVisible(appPage.getByLabel(Pattern.compile("(?i)Nombre del Negocio")));
		if (input != null) {
			input.fill("Negocio Prueba Automatización");
		}

		clickByName(appPage, Pattern.compile("(?i)^Cancelar$"));
		waitForUiLoad(appPage);
	}

	private void openAdministrarNegocios() {
		clickByName(appPage, Pattern.compile("(?i)^Mi Negocio$"));
		clickByName(appPage, Pattern.compile("(?i)^Administrar Negocios$"));

		waitForVisibleText(appPage, Pattern.compile("(?i)^Información General$"), 30_000);
		waitForVisibleText(appPage, Pattern.compile("(?i)^Detalles de la Cuenta$"), 30_000);
		waitForVisibleText(appPage, Pattern.compile("(?i)^Tus Negocios$"), 30_000);
		waitForVisibleText(appPage, Pattern.compile("(?i)(Secci[oó]n Legal|Legal)"), 30_000);

		captureScreenshot(appPage, "04_administrar_negocios_page", true);
	}

	private void validateInformacionGeneral(final String googleEmail, final String expectedUserName) {
		waitForVisibleText(appPage, Pattern.compile("(?i)^Información General$"), 20_000);
		waitForVisibleText(appPage, Pattern.compile("(?i)BUSINESS\\s+PLAN"), 20_000);
		waitForVisibleText(appPage, Pattern.compile("(?i)^Cambiar Plan$"), 20_000);

		final String bodyText = appPage.locator("body").innerText();
		assertTrue("No se encontró un email visible en Información General.", EMAIL_PATTERN.matcher(bodyText).find());

		if (!expectedUserName.isBlank()) {
			waitForVisibleText(appPage, Pattern.compile(Pattern.quote(expectedUserName), Pattern.CASE_INSENSITIVE), 20_000);
		} else {
			assertTrue("No se detectó el email esperado del usuario autenticado.",
					bodyText.toLowerCase().contains(googleEmail.toLowerCase()));
		}
	}

	private void validateDetallesCuenta() {
		waitForVisibleText(appPage, Pattern.compile("(?i)^Detalles de la Cuenta$"), 20_000);
		waitForVisibleText(appPage, Pattern.compile("(?i)Cuenta creada"), 20_000);
		waitForVisibleText(appPage, Pattern.compile("(?i)Estado activo"), 20_000);
		waitForVisibleText(appPage, Pattern.compile("(?i)Idioma seleccionado"), 20_000);
	}

	private void validateTusNegocios() {
		waitForVisibleText(appPage, Pattern.compile("(?i)^Tus Negocios$"), 20_000);
		waitForVisibleText(appPage, Pattern.compile("(?i)^Agregar Negocio$"), 20_000);
		waitForVisibleText(appPage, Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios"), 20_000);

		final Locator listLikeContainer = appPage.locator("ul,ol,table,[role='list'],[role='table']").first();
		assertTrue("No se detectó una lista visible de negocios.", listLikeContainer.isVisible());
	}

	private void openAndValidateLegalPage(final String linkText) {
		final int pagesBeforeClick = context.pages().size();
		clickByName(appPage, Pattern.compile("(?i)^" + Pattern.quote(linkText) + "$"));

		waitForUiLoad(appPage);
		Page legalPage = appPage;

		if (context.pages().size() > pagesBeforeClick) {
			legalPage = context.pages().get(context.pages().size() - 1);
			waitForUiLoad(legalPage);
		}

		waitForVisibleText(legalPage, Pattern.compile("(?i)^" + Pattern.quote(linkText) + "$"), 30_000);
		final String legalContent = legalPage.locator("body").innerText().replaceAll("\\s+", " ").trim();
		assertTrue("No se encontró contenido legal suficiente para " + linkText + ".", legalContent.length() > 120);

		captureScreenshot(legalPage, "05_" + sanitize(linkText) + "_page", true);
		legalUrls.put(linkText, legalPage.url());

		if (legalPage != appPage) {
			legalPage.close();
		}
		appPage.bringToFront();
		waitForUiLoad(appPage);
	}

	private void runStep(final String stepName, final Runnable step) {
		try {
			step.run();
			report.put(stepName, true);
		} catch (final RuntimeException exception) {
			report.put(stepName, false);
			stepErrors.put(stepName, exception.getMessage());
			try {
				captureScreenshot(appPage, "error_" + sanitize(stepName), true);
			} catch (final RuntimeException ignored) {
				// Best effort screenshot on failure.
			}
		}
	}

	private void printFinalReport() {
		System.out.println();
		System.out.println("===== SaleADS Mi Negocio Workflow - Final Report =====");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			final String status = entry.getValue() ? "PASS" : "FAIL";
			System.out.println("- " + entry.getKey() + ": " + status);
			if (!entry.getValue() && stepErrors.containsKey(entry.getKey())) {
				System.out.println("  reason: " + stepErrors.get(entry.getKey()));
			}
		}

		if (!legalUrls.isEmpty()) {
			System.out.println("Legal URLs:");
			for (final Map.Entry<String, String> legalEntry : legalUrls.entrySet()) {
				System.out.println("  " + legalEntry.getKey() + " -> " + legalEntry.getValue());
			}
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		System.out.println("======================================================");
		System.out.println();
	}

	private void clickByName(final Page page, final Pattern pattern) {
		final Locator locator = requireVisible(findVisibleByName(page, pattern),
				"No se encontró elemento clickable: " + pattern.pattern());
		locator.click();
		waitForUiLoad(page);
	}

	private Locator findVisibleByName(final Page page, final Pattern pattern) {
		final List<Locator> candidates = Arrays.asList(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern)),
				page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(pattern)),
				page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(pattern)), page.getByText(pattern));

		for (final Locator candidate : candidates) {
			final Locator visible = firstVisible(candidate);
			if (visible != null) {
				return visible;
			}
		}

		return null;
	}

	private Locator firstVisible(final Locator locator) {
		final int count = Math.min(locator.count(), 20);
		for (int index = 0; index < count; index++) {
			final Locator nth = locator.nth(index);
			if (nth.isVisible()) {
				return nth;
			}
		}
		return null;
	}

	private <T> T requireVisible(final T value, final String message) {
		if (value == null) {
			throw new IllegalStateException(message);
		}
		return value;
	}

	private boolean hasVisible(final Locator locator) {
		if (locator.count() == 0) {
			return false;
		}
		return locator.first().isVisible();
	}

	private void waitForVisibleText(final Page page, final Pattern pattern, final double timeoutMs) {
		final Locator locator = page.getByText(pattern).first();
		locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
	}

	private void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15_000));
		} catch (final RuntimeException ignored) {
			// Some UI transitions are client-side only.
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7_000));
		} catch (final RuntimeException ignored) {
			// Network idle is best effort.
		}

		page.waitForTimeout(800);
	}

	private void captureScreenshot(final Page page, final String checkpointName, final boolean fullPage) {
		if (page == null || page.isClosed()) {
			return;
		}

		final String filename = String.format("%02d_%s.png", screenshotCounter++, sanitize(checkpointName));
		final Path screenshotPath = evidenceDir.resolve(filename);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	private String readConfig(final String envName, final String propertyName, final String defaultValue) {
		final String fromProperty = System.getProperty(propertyName);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}

		final String fromEnv = System.getenv(envName);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}

		return defaultValue;
	}

	private String sanitize(final String rawValue) {
		return rawValue.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
	}
}
