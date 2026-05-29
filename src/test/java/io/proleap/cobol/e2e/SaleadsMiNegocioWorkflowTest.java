package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;

/**
 * SaleADS.ai E2E workflow for Google login + Mi Negocio validation.
 *
 * <p>This test is environment-agnostic and avoids hardcoded domains.
 * To execute it you must provide either:
 * <ul>
 *   <li>SALEADS_CDP_URL (connect to an already opened browser tab on login page), or</li>
 *   <li>SALEADS_LOGIN_URL (test navigates to login page in a new browser).</li>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Política de Privacidad";

	private final LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();
	private final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page appPage;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		evidenceDir = Files.createDirectories(Paths.get("target", "saleads-evidence",
				DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())));

		playwright = Playwright.create();
		final String cdpUrl = env("SALEADS_CDP_URL");
		if (cdpUrl != null) {
			browser = playwright.chromium().connectOverCDP(cdpUrl);
			context = browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
		} else {
			final boolean headless = !"false".equalsIgnoreCase(envOrDefault("HEADLESS", "true"));
			browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
		}

		final String loginUrl = env("SALEADS_LOGIN_URL");
		if (loginUrl == null && !hasNonBlankOpenPage()) {
			Assume.assumeTrue(
					"No start page available. Provide SALEADS_LOGIN_URL, or SALEADS_CDP_URL with an already opened login tab.",
					false);
		}
		appPage = resolveInitialPage(loginUrl);
		appPage.setDefaultTimeout(15000);

		initializeReport();
	}

	@After
	public void tearDown() {
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
		report.put(REPORT_LOGIN, runStep(this::stepLoginWithGoogle));
		report.put(REPORT_MI_NEGOCIO_MENU, runStep(this::stepOpenMiNegocioMenu));
		report.put(REPORT_AGREGAR_MODAL, runStep(this::stepValidateAgregarNegocioModal));
		report.put(REPORT_ADMINISTRAR_VIEW, runStep(this::stepOpenAdministrarNegocios));
		report.put(REPORT_INFO_GENERAL, runStep(this::stepValidateInformacionGeneral));
		report.put(REPORT_DETALLES, runStep(this::stepValidateDetallesCuenta));
		report.put(REPORT_TUS_NEGOCIOS, runStep(this::stepValidateTusNegocios));
		report.put(REPORT_TERMINOS, runStep(() -> {
			final String url = stepValidateLegalPage("Términos y Condiciones", "terms-and-conditions");
			legalUrls.put(REPORT_TERMINOS, url);
		}));
		report.put(REPORT_PRIVACIDAD, runStep(() -> {
			final String url = stepValidateLegalPage("Política de Privacidad", "privacy-policy");
			legalUrls.put(REPORT_PRIVACIDAD, url);
		}));

		printFinalReport();

		final List<String> failed = new ArrayList<>();
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			if (!entry.getValue()) {
				failed.add(entry.getKey());
			}
		}
		Assert.assertTrue("Validation failures: " + failed, failed.isEmpty());
	}

	private void stepLoginWithGoogle() {
		clickByVisibleText(appPage, "Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		waitForUi(appPage);

		selectGoogleAccountIfVisible(ACCOUNT_EMAIL);
		waitForSidebar();

		assertTextVisible(appPage, "Negocio");
		takeScreenshot("01-dashboard-loaded", appPage, true);
	}

	private void stepOpenMiNegocioMenu() {
		clickByVisibleText(appPage, "Negocio");
		waitForUi(appPage);
		clickByVisibleText(appPage, "Mi Negocio");
		waitForUi(appPage);

		assertTextVisible(appPage, "Agregar Negocio");
		assertTextVisible(appPage, "Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded", appPage, false);
	}

	private void stepValidateAgregarNegocioModal() {
		clickByVisibleText(appPage, "Agregar Negocio");
		waitForUi(appPage);

		assertTextVisible(appPage, "Crear Nuevo Negocio");
		assertVisibleAny(appPage, "Nombre del Negocio", "Nombre negocio");
		assertTextVisible(appPage, "Tienes 2 de 3 negocios");
		assertTextVisible(appPage, "Cancelar");
		assertTextVisible(appPage, "Crear Negocio");

		fillIfVisible(appPage, "Nombre del Negocio", "Negocio Prueba Automatización");
		takeScreenshot("03-agregar-negocio-modal", appPage, false);
		clickByVisibleText(appPage, "Cancelar");
		waitForUi(appPage);
	}

	private void stepOpenAdministrarNegocios() {
		if (!isTextVisible(appPage, "Administrar Negocios")) {
			clickByVisibleText(appPage, "Mi Negocio");
			waitForUi(appPage);
		}
		clickByVisibleText(appPage, "Administrar Negocios");
		waitForUi(appPage);

		assertTextVisible(appPage, "Información General");
		assertTextVisible(appPage, "Detalles de la Cuenta");
		assertTextVisible(appPage, "Tus Negocios");
		assertVisibleAny(appPage, "Sección Legal", "Seccion Legal");

		takeScreenshot("04-administrar-negocios", appPage, true);
	}

	private void stepValidateInformacionGeneral() {
		assertVisibleAny(appPage, "Nombre", "Usuario", "Perfil", "juan", "Juan");
		assertEmailVisible(appPage);
		assertTextVisible(appPage, "BUSINESS PLAN");
		assertTextVisible(appPage, "Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		assertTextVisible(appPage, "Cuenta creada");
		assertVisibleAny(appPage, "Estado activo", "Estado Activo");
		assertVisibleAny(appPage, "Idioma seleccionado", "Idioma Seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertTextVisible(appPage, "Tus Negocios");
		assertTextVisible(appPage, "Agregar Negocio");
		assertTextVisible(appPage, "Tienes 2 de 3 negocios");

		final int negocioMentions = appPage.locator("text=Negocio").count();
		Assert.assertTrue("Business list is not visible.", negocioMentions >= 2);
	}

	private String stepValidateLegalPage(final String linkText, final String screenshotPrefix) {
		final Page startingPage = appPage;
		Page legalPage = null;
		boolean openedNewTab = false;

		try {
			legalPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(7000),
					() -> clickByVisibleText(startingPage, linkText));
			openedNewTab = true;
		} catch (TimeoutError ignored) {
			clickByVisibleText(startingPage, linkText);
			legalPage = startingPage;
		}

		waitForUi(legalPage);
		assertTextVisible(legalPage, linkText);
		assertLegalContentVisible(legalPage);
		takeScreenshot("05-" + screenshotPrefix, legalPage, true);
		final String finalUrl = legalPage.url();

		if (openedNewTab) {
			legalPage.close();
			startingPage.bringToFront();
			appPage = startingPage;
		} else {
			try {
				legalPage.goBack();
				waitForUi(legalPage);
			} catch (Exception ignored) {
				// Keep current page if browser disallows back.
			}
			appPage = legalPage;
		}

		return finalUrl;
	}

	private void selectGoogleAccountIfVisible(final String email) {
		final long deadline = System.currentTimeMillis() + 20000;
		while (System.currentTimeMillis() < deadline) {
			for (Page page : context.pages()) {
				if (isTextVisible(page, email)) {
					page.bringToFront();
					clickByVisibleText(page, email);
					waitForUi(page);
					return;
				}
			}
			waitForUi(appPage);
		}
	}

	private void waitForSidebar() {
		final long deadline = System.currentTimeMillis() + 60000;
		while (System.currentTimeMillis() < deadline) {
			for (Page page : context.pages()) {
				if (isTextVisible(page, "Negocio")) {
					page.bringToFront();
					appPage = page;
					return;
				}
			}
			waitForUi(appPage);
		}
		throw new AssertionError("Main interface/sidebar did not appear after login.");
	}

	private void fillIfVisible(final Page page, final String label, final String value) {
		Locator input = page.getByLabel(label).first();
		if (input.count() == 0) {
			input = page.getByPlaceholder(label).first();
		}
		if (input.count() > 0 && input.isVisible()) {
			input.fill(value);
		}
	}

	private void assertEmailVisible(final Page page) {
		final Locator emailLocator = page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").first();
		Assert.assertTrue("User email is not visible.", emailLocator.count() > 0 && emailLocator.isVisible());
	}

	private void assertLegalContentVisible(final Page page) {
		final Locator paragraphs = page.locator("p");
		Assert.assertTrue("Legal content text is not visible.", paragraphs.count() > 0 && paragraphs.first().isVisible());
	}

	private void assertTextVisible(final Page page, final String text) {
		Assert.assertTrue("Expected visible text: " + text, isTextVisible(page, text));
	}

	private void assertVisibleAny(final Page page, final String... texts) {
		for (String text : texts) {
			if (isTextVisible(page, text)) {
				return;
			}
		}
		Assert.fail("None of these texts were visible: " + Arrays.toString(texts));
	}

	private boolean isTextVisible(final Page page, final String text) {
		final Locator byText = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
		return byText.count() > 0 && byText.isVisible();
	}

	private void clickByVisibleText(final Page page, final String... texts) {
		for (String text : texts) {
			final Locator button = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text)).first();
			if (button.count() > 0 && button.isVisible()) {
				button.click();
				waitForUi(page);
				return;
			}

			final Locator link = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text)).first();
			if (link.count() > 0 && link.isVisible()) {
				link.click();
				waitForUi(page);
				return;
			}

			final Locator textLocator = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
			if (textLocator.count() > 0 && textLocator.isVisible()) {
				textLocator.click();
				waitForUi(page);
				return;
			}
		}
		throw new AssertionError("Could not click any visible element matching: " + Arrays.toString(texts));
	}

	private void waitForUi(final Page page) {
		if (page == null || page.isClosed()) {
			return;
		}
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (Exception ignored) {
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (Exception ignored) {
		}
		page.waitForTimeout(700);
	}

	private void takeScreenshot(final String checkpoint, final Page page, final boolean fullPage) {
		try {
			final Path target = evidenceDir.resolve(checkpoint + ".png");
			page.screenshot(new Page.ScreenshotOptions().setPath(target).setFullPage(fullPage));
		} catch (Exception e) {
			throw new AssertionError("Failed to capture screenshot: " + checkpoint, e);
		}
	}

	private Page resolveInitialPage(final String loginUrl) {
		Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
		if (loginUrl != null && !loginUrl.trim().isEmpty()) {
			page.navigate(loginUrl.trim());
			waitForUi(page);
			return page;
		}

		if (!"about:blank".equals(page.url())) {
			return page;
		}

		throw new IllegalStateException(
				"No start page available. Provide SALEADS_LOGIN_URL, or SALEADS_CDP_URL with an already opened login tab.");
	}

	private boolean hasNonBlankOpenPage() {
		for (Page page : context.pages()) {
			if (page != null && !page.isClosed() && !"about:blank".equals(page.url())) {
				return true;
			}
		}
		return false;
	}

	private boolean runStep(final Step step) {
		try {
			step.run();
			return true;
		} catch (Throwable t) {
			System.err.println("[FAIL] " + t.getMessage());
			return false;
		}
	}

	private void printFinalReport() {
		System.out.println("==== SALEADS MI NEGOCIO WORKFLOW REPORT ====");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
			System.out.println(entry.getKey() + " URL: " + entry.getValue());
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
	}

	private void initializeReport() {
		report.put(REPORT_LOGIN, false);
		report.put(REPORT_MI_NEGOCIO_MENU, false);
		report.put(REPORT_AGREGAR_MODAL, false);
		report.put(REPORT_ADMINISTRAR_VIEW, false);
		report.put(REPORT_INFO_GENERAL, false);
		report.put(REPORT_DETALLES, false);
		report.put(REPORT_TUS_NEGOCIOS, false);
		report.put(REPORT_TERMINOS, false);
		report.put(REPORT_PRIVACIDAD, false);
	}

	private String env(final String name) {
		final String value = System.getenv(name);
		return value == null || value.trim().isEmpty() ? null : value;
	}

	private String envOrDefault(final String name, final String fallback) {
		final String value = env(name);
		return value == null ? fallback : value;
	}

	private interface Step {
		void run();
	}
}
