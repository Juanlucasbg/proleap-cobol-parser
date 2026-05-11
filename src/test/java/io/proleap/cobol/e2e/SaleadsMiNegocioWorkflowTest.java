package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
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

/**
 * End-to-end workflow test for SaleADS "Mi Negocio" module.
 *
 * <p>
 * This test is environment-agnostic: it does not hardcode any domain and reads the
 * login/start URL from SALEADS_START_URL or SALEADS_BASE_URL.
 * </p>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Path SCREENSHOT_DIR = Paths.get("target", "saleads-evidence");
	private static final double SHORT_TIMEOUT_MS = 2_500;
	private static final double DEFAULT_TIMEOUT_MS = 15_000;

	private final Map<String, String> reportByStep = new LinkedHashMap<>();
	private final Map<String, String> evidenceUrls = new LinkedHashMap<>();

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page appPage;
	private int screenshotCounter;

	@Before
	public void setUp() throws Exception {
		Files.createDirectories(SCREENSHOT_DIR);
		screenshotCounter = 0;

		final boolean headless = Boolean.parseBoolean(readValue("SALEADS_HEADLESS", "saleads.headless", "true"));

		playwright = Playwright.create();
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
		appPage = context.newPage();
		appPage.navigate(resolveStartUrl());
		waitForUiLoad(appPage);
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
		boolean allPassed = true;

		allPassed &= runStep("Login", this::stepLoginWithGoogle);
		allPassed &= runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		allPassed &= runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		allPassed &= runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		allPassed &= runStep("Información General", this::stepValidateInformacionGeneral);
		allPassed &= runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		allPassed &= runStep("Tus Negocios", this::stepValidateTusNegocios);
		allPassed &= runStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones"));
		allPassed &= runStep("Política de Privacidad", () -> stepValidateLegalLink("Política de Privacidad"));

		Assert.assertTrue("Some validations failed. Check console report and screenshots in target/saleads-evidence.", allPassed);
	}

	private void stepLoginWithGoogle() {
		final Locator loginButton = findFirstVisibleAction("Sign in with Google", "Iniciar sesión con Google",
				"Iniciar con Google", "Ingresar con Google", "Continuar con Google");

		final int pagesBeforeClick = context.pages().size();
		clickAndWait(appPage, loginButton);
		selectGoogleAccountIfVisible(appPage);

		if (context.pages().size() > pagesBeforeClick) {
			final Page popupPage = getNewestPage();
			popupPage.bringToFront();
			waitForUiLoad(popupPage);
			selectGoogleAccountIfVisible(popupPage);
		}

		appPage.bringToFront();
		waitForUiLoad(appPage);
		assertAnyVisible("main application interface", appPage.locator("main").first(),
				appPage.getByRole(AriaRole.MAIN).first(), appPage.getByText(Pattern.compile("(?i).*dashboard.*")).first());
		assertAnyVisible("left sidebar navigation", appPage.locator("aside").first(),
				appPage.getByRole(AriaRole.NAVIGATION).first(), appPage.getByText(Pattern.compile("(?i).*negocio.*")).first());
		captureScreenshot("01-dashboard-loaded", appPage, false);
	}

	private void stepOpenMiNegocioMenu() {
		clickAndWait(appPage, findFirstVisibleAction("Negocio"));
		clickAndWait(appPage, findFirstVisibleAction("Mi Negocio"));

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded", appPage, false);
	}

	private void stepValidateAgregarNegocioModal() {
		clickAndWait(appPage, findFirstVisibleAction("Agregar Negocio"));

		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertAnyVisible("cancel button", appPage.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*cancelar.*"))).first(),
				appPage.getByText(Pattern.compile("(?i).*cancelar.*")).first());
		assertAnyVisible("create business button", appPage.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*crear negocio.*"))).first(),
				appPage.getByText(Pattern.compile("(?i).*crear negocio.*")).first());

		final Locator input = appPage.locator("input, textarea").first();
		assertAnyVisible("business name input field", input);
		input.click();
		input.fill("Negocio Prueba Automatización");

		captureScreenshot("03-crear-negocio-modal", appPage, false);
		clickAndWait(appPage, findFirstVisibleAction("Cancelar"));
	}

	private void stepOpenAdministrarNegocios() {
		ensureMiNegocioMenuExpanded();
		clickAndWait(appPage, findFirstVisibleAction("Administrar Negocios"));

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertAnyVisible("legal section", appPage.getByText(Pattern.compile("(?i).*sección legal.*")).first(),
				appPage.getByText(Pattern.compile("(?i).*legal.*")).first());
		captureScreenshot("04-administrar-negocios", appPage, true);
	}

	private void stepValidateInformacionGeneral() {
		assertAnyVisible("user name field", appPage.getByText(Pattern.compile("(?i).*(nombre|usuario|user).*")).first(),
				appPage.getByText(Pattern.compile("(?i).*juan.*")).first());
		assertEmailVisibleOnPage(appPage);
		assertVisibleText("BUSINESS PLAN");
		assertAnyVisible("Cambiar Plan button", appPage.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*cambiar plan.*"))).first(),
				appPage.getByText(Pattern.compile("(?i).*cambiar plan.*")).first());
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertAnyVisible("business list", appPage.locator("table, [role='list'], ul, ol").first(),
				appPage.getByText(Pattern.compile("(?i).*negocio.*")).first());
		assertVisibleText("Tienes 2 de 3 negocios");
		assertAnyVisible("Agregar Negocio button", appPage.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*agregar negocio.*"))).first(),
				appPage.getByText(Pattern.compile("(?i).*agregar negocio.*")).first());
	}

	private void stepValidateLegalLink(final String legalLinkText) {
		ensureMiNegocioMenuExpanded();
		final Page destination = clickAndResolvePossibleNewTab(findFirstVisibleAction(legalLinkText));

		assertAnyVisible("legal heading " + legalLinkText,
				destination.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
						.setName(Pattern.compile("(?i).*" + Pattern.quote(legalLinkText) + ".*")))
						.first(),
				destination.getByText(Pattern.compile("(?i).*" + Pattern.quote(legalLinkText) + ".*")).first());
		assertLegalBodyContentVisible(destination);

		captureScreenshot("05-" + normalizeName(legalLinkText), destination, true);
		evidenceUrls.put(legalLinkText, destination.url());

		if (destination != appPage) {
			destination.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else {
			appPage.goBack();
			waitForUiLoad(appPage);
		}
	}

	private void ensureMiNegocioMenuExpanded() {
		if (!isVisible(appPage.getByText(Pattern.compile("(?i).*administrar negocios.*")).first(), SHORT_TIMEOUT_MS)) {
			clickAndWait(appPage, findFirstVisibleAction("Negocio"));
			clickAndWait(appPage, findFirstVisibleAction("Mi Negocio"));
		}
	}

	private boolean runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			reportByStep.put(stepName, "PASS");
			return true;
		} catch (final Throwable error) {
			reportByStep.put(stepName, "FAIL - " + error.getMessage());
			return false;
		}
	}

	private void printFinalReport() {
		if (reportByStep.isEmpty()) {
			return;
		}

		System.out.println();
		System.out.println("=== SaleADS Mi Negocio Final Report ===");
		final List<String> orderedFields = new ArrayList<>();
		orderedFields.add("Login");
		orderedFields.add("Mi Negocio menu");
		orderedFields.add("Agregar Negocio modal");
		orderedFields.add("Administrar Negocios view");
		orderedFields.add("Información General");
		orderedFields.add("Detalles de la Cuenta");
		orderedFields.add("Tus Negocios");
		orderedFields.add("Términos y Condiciones");
		orderedFields.add("Política de Privacidad");

		for (final String field : orderedFields) {
			final String status = reportByStep.getOrDefault(field, "NOT EXECUTED");
			System.out.println(field + ": " + status);
		}

		if (!evidenceUrls.isEmpty()) {
			System.out.println("--- Legal URLs ---");
			evidenceUrls.forEach((name, url) -> System.out.println(name + ": " + url));
		}
	}

	private String resolveStartUrl() {
		final String startUrl = firstNonBlank(System.getenv("SALEADS_START_URL"), System.getenv("SALEADS_BASE_URL"),
				System.getProperty("saleads.startUrl"), System.getProperty("saleads.baseUrl"));
		if (startUrl == null) {
			throw new IllegalStateException(
					"Missing start URL. Set SALEADS_START_URL or SALEADS_BASE_URL (or -Dsaleads.startUrl/-Dsaleads.baseUrl).");
		}
		return startUrl;
	}

	private void clickAndWait(final Page page, final Locator target) {
		target.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
		target.click();
		waitForUiLoad(page);
	}

	private void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final PlaywrightException ignored) {
			// Some click actions do not trigger navigation. A short pause is still needed.
		}
		page.waitForTimeout(500);
	}

	private void selectGoogleAccountIfVisible(final Page targetPage) {
		final Locator accountOption = targetPage.getByText(Pattern.compile("(?i).*" + Pattern.quote(GOOGLE_ACCOUNT_EMAIL) + ".*"))
				.first();
		if (isVisible(accountOption, SHORT_TIMEOUT_MS)) {
			clickAndWait(targetPage, accountOption);
		}
	}

	private Page clickAndResolvePossibleNewTab(final Locator trigger) {
		final int pagesBeforeClick = context.pages().size();
		trigger.click();
		waitForUiLoad(appPage);

		if (context.pages().size() > pagesBeforeClick) {
			final Page newTab = getNewestPage();
			newTab.bringToFront();
			waitForUiLoad(newTab);
			return newTab;
		}
		return appPage;
	}

	private Page getNewestPage() {
		final List<Page> pages = context.pages();
		return pages.get(pages.size() - 1);
	}

	private Locator findFirstVisibleAction(final String... texts) {
		for (final String text : texts) {
			final Pattern textPattern = Pattern.compile("(?i).*" + Pattern.quote(text) + ".*");

			final List<Locator> candidates = List.of(
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern)).first(),
					appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(textPattern)).first(),
					appPage.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(textPattern)).first(),
					appPage.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(textPattern)).first(),
					appPage.getByText(textPattern).first());

			for (final Locator candidate : candidates) {
				if (isVisible(candidate, SHORT_TIMEOUT_MS)) {
					return candidate;
				}
			}
		}

		throw new AssertionError("No visible actionable element found for texts: " + String.join(", ", texts));
	}

	private void assertVisibleText(final String text) {
		final Pattern pattern = Pattern.compile("(?i).*" + Pattern.quote(text) + ".*");
		assertAnyVisible("text: " + text, appPage.getByText(pattern).first(),
				appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(pattern)).first());
	}

	private void assertAnyVisible(final String description, final Locator... candidates) {
		for (final Locator candidate : candidates) {
			if (isVisible(candidate, DEFAULT_TIMEOUT_MS)) {
				return;
			}
		}
		throw new AssertionError("Expected visible element not found for " + description);
	}

	private void assertEmailVisibleOnPage(final Page page) {
		final String bodyText = page.locator("body").innerText();
		final Matcher matcher = EMAIL_PATTERN.matcher(bodyText);
		assertTrue("Expected an email to be visible on the page.", matcher.find());
	}

	private void assertLegalBodyContentVisible(final Page page) {
		final String bodyText = page.locator("body").innerText();
		assertTrue("Legal content text should be visible.", bodyText != null && bodyText.trim().length() > 120);
	}

	private boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (final PlaywrightException error) {
			return false;
		}
	}

	private void captureScreenshot(final String name, final Page page, final boolean fullPage) {
		final String fileName = String.format("%02d-%s.png", ++screenshotCounter, normalizeName(name));
		final Path screenshotPath = SCREENSHOT_DIR.resolve(fileName);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
		System.out.println("Screenshot captured: " + screenshotPath.toAbsolutePath());
	}

	private String normalizeName(final String input) {
		return input.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String readValue(final String envName, final String propertyName, final String fallback) {
		return firstNonBlank(System.getenv(envName), System.getProperty(propertyName), fallback);
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
