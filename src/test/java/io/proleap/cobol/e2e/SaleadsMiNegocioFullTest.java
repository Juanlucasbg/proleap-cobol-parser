package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

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
import com.microsoft.playwright.options.WaitUntilState;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final int DEFAULT_TIMEOUT_MS = 20000;

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private Path evidenceDir;

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String enabled = System.getenv("SALEADS_E2E_ENABLED");
		assumeTrue("Set SALEADS_E2E_ENABLED=true to run the SaleADS E2E workflow test.",
				"true".equalsIgnoreCase(enabled));

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		assumeTrue(
				"Set SALEADS_LOGIN_URL to the SaleADS login page for the current environment (dev/staging/prod).",
				loginUrl != null && !loginUrl.isBlank());

		evidenceDir = Paths.get("target", "saleads-evidence", String.valueOf(Instant.now().toEpochMilli()));
		Files.createDirectories(evidenceDir);

		try (Playwright playwright = Playwright.create()) {
			final boolean headless = !"false".equalsIgnoreCase(System.getenv("SALEADS_HEADLESS"));
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser
					.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
			final Page appPage = context.newPage();

			appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUi(appPage);

			runStep("Login", () -> {
				loginWithGoogle(context, appPage);
				validateMainInterfaceLoaded(appPage);
				screenshot(appPage, "01-dashboard-loaded.png", false);
				return "Dashboard loaded and sidebar visible.";
			});

			runStep("Mi Negocio menu", () -> {
				openMiNegocioMenu(appPage);
				assertTextVisible(appPage, "Agregar Negocio");
				assertTextVisible(appPage, "Administrar Negocios");
				screenshot(appPage, "02-mi-negocio-menu-expanded.png", false);
				return "Mi Negocio submenu expanded with expected options.";
			});

			runStep("Agregar Negocio modal", () -> {
				clickByVisibleText(appPage, "Agregar Negocio");
				assertTextVisible(appPage, "Crear Nuevo Negocio");
				assertTextVisible(appPage, "Nombre del Negocio");
				assertTextVisible(appPage, "Tienes 2 de 3 negocios");
				assertTextVisible(appPage, "Cancelar");
				assertTextVisible(appPage, "Crear Negocio");
				screenshot(appPage, "03-agregar-negocio-modal.png", false);

				fillOptionalBusinessNameAndCancel(appPage, "Negocio Prueba Automatización");
				return "Modal validated and closed with optional input flow.";
			});

			runStep("Administrar Negocios view", () -> {
				openMiNegocioMenu(appPage);
				clickByVisibleText(appPage, "Administrar Negocios");
				assertTextVisible(appPage, "Información General");
				assertTextVisible(appPage, "Detalles de la Cuenta");
				assertTextVisible(appPage, "Tus Negocios");
				assertTextVisible(appPage, "Sección Legal");
				screenshot(appPage, "04-administrar-negocios-view.png", true);
				return "Administrar Negocios page sections are visible.";
			});

			runStep("Información General", () -> {
				assertTextVisible(appPage, "BUSINESS PLAN");
				assertTextVisible(appPage, "Cambiar Plan");
				assertEmailAndNameVisible(appPage);
				return "Nombre, email, plan and action button validated.";
			});

			runStep("Detalles de la Cuenta", () -> {
				assertTextVisible(appPage, "Cuenta creada");
				assertTextVisible(appPage, "Estado activo");
				assertTextVisible(appPage, "Idioma seleccionado");
				return "Detalles de la cuenta validated.";
			});

			runStep("Tus Negocios", () -> {
				assertTextVisible(appPage, "Tus Negocios");
				assertTextVisible(appPage, "Agregar Negocio");
				assertTextVisible(appPage, "Tienes 2 de 3 negocios");
				final String pageText = appPage.locator("body").innerText();
				assertTrue("Expected business list content in 'Tus Negocios' section.", pageText.contains("Negocio"));
				return "Business list, button and quota text validated.";
			});

			runStep("Términos y Condiciones", () -> {
				final Page termsPage = openLegalLink(context, appPage, "Términos y Condiciones");
				assertTextVisible(termsPage, "Términos y Condiciones");
				assertLegalContentVisible(termsPage);
				screenshot(termsPage, "05-terminos-y-condiciones.png", true);
				final String finalUrl = termsPage.url();
				returnToAppTab(appPage, termsPage);
				return "Validated legal page URL: " + finalUrl;
			});

			runStep("Política de Privacidad", () -> {
				final Page privacyPage = openLegalLink(context, appPage, "Política de Privacidad");
				assertTextVisible(privacyPage, "Política de Privacidad");
				assertLegalContentVisible(privacyPage);
				screenshot(privacyPage, "06-politica-de-privacidad.png", true);
				final String finalUrl = privacyPage.url();
				returnToAppTab(appPage, privacyPage);
				return "Validated legal page URL: " + finalUrl;
			});
		}

		completeMissingReportEntries();
		printReport();
		assertAllStepsPassed();
	}

	private void loginWithGoogle(final BrowserContext context, final Page appPage) {
		final Locator loginButton = waitForAnyVisible(appPage, "Google login button",
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("Google|Iniciar|Sign in", Pattern.CASE_INSENSITIVE))),
				appPage.getByText(Pattern.compile("Google", Pattern.CASE_INSENSITIVE)));

		final Page popup = clickAndMaybeOpenPopup(context, appPage, loginButton);
		selectGoogleAccountIfPrompted(context, appPage, popup);
		waitForUi(appPage);
	}

	private void validateMainInterfaceLoaded(final Page appPage) {
		assertTextVisible(appPage, "Negocio");
		final Locator sidebar = waitForAnyVisible(appPage, "left sidebar navigation", appPage.locator("aside"),
				appPage.locator("nav"));
		assertTrue("Expected left sidebar to be visible.", sidebar.isVisible());
	}

	private void openMiNegocioMenu(final Page appPage) {
		Locator miNegocio = findAnyVisible(appPage,
				appPage.getByText(Pattern.compile("^Mi\\s+Negocio$", Pattern.CASE_INSENSITIVE)),
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("Mi\\s+Negocio", Pattern.CASE_INSENSITIVE))));

		if (miNegocio == null) {
			clickByVisibleText(appPage, "Negocio");
			miNegocio = waitForAnyVisible(appPage, "Mi Negocio option",
					appPage.getByText(Pattern.compile("^Mi\\s+Negocio$", Pattern.CASE_INSENSITIVE)),
					appPage.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(Pattern.compile("Mi\\s+Negocio", Pattern.CASE_INSENSITIVE))));
		}

		clickAndWait(appPage, miNegocio);

		if (!isTextVisible(appPage, "Agregar Negocio") || !isTextVisible(appPage, "Administrar Negocios")) {
			clickAndWait(appPage, miNegocio);
		}
	}

	private void fillOptionalBusinessNameAndCancel(final Page appPage, final String businessName) {
		final Locator businessNameInput = findAnyVisible(appPage,
				appPage.getByLabel(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE)),
				appPage.getByPlaceholder(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE)),
				appPage.locator("input[name*='nombre'], input[id*='nombre'], input[type='text']"));

		if (businessNameInput != null) {
			businessNameInput.fill(businessName);
			waitForUi(appPage);
		}

		clickByVisibleText(appPage, "Cancelar");
	}

	private void assertEmailAndNameVisible(final Page appPage) {
		final Locator emailLocator = waitForAnyVisible(appPage, "user email text",
				appPage.locator("xpath=//*[contains(text(),'@')]"));
		assertTrue("Expected user email to be visible.", emailLocator.isVisible());

		final String emailLine = emailLocator.innerText().trim();
		assertTrue("Email text should contain '@'.", emailLine.contains("@"));

		final String surroundingText = emailLocator.locator("xpath=ancestor::*[1]").innerText();
		final String withoutEmail = surroundingText.replace(emailLine, "").trim();
		assertTrue("Expected user name to be visible near user email.", !withoutEmail.isEmpty());
	}

	private Page openLegalLink(final BrowserContext context, final Page appPage, final String linkText) {
		assertTextVisible(appPage, "Sección Legal");
		final Locator link = waitForAnyVisible(appPage, linkText, appPage.getByText(linkText), appPage.getByRole(AriaRole.LINK,
				new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(linkText), Pattern.CASE_INSENSITIVE))));

		return clickAndGetTargetPage(context, appPage, link);
	}

	private void assertLegalContentVisible(final Page page) {
		final String bodyText = page.locator("body").innerText().replaceAll("\\s+", " ").trim();
		assertTrue("Expected legal content text to be visible.", bodyText.length() > 200);
	}

	private void returnToAppTab(final Page appPage, final Page legalPage) {
		if (legalPage != appPage && !legalPage.isClosed()) {
			legalPage.close();
		}

		appPage.bringToFront();
		waitForUi(appPage);

		if (!isTextVisible(appPage, "Sección Legal")) {
			appPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUi(appPage);
		}
	}

	private Page clickAndGetTargetPage(final BrowserContext context, final Page sourcePage, final Locator link) {
		link.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));

		try {
			final Page popup = context.waitForPage(
					() -> link.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS)),
					new BrowserContext.WaitForPageOptions().setTimeout(8000));
			waitForUi(popup);
			return popup;
		} catch (final PlaywrightException e) {
			waitForUi(sourcePage);
			return sourcePage;
		}
	}

	private Page clickAndMaybeOpenPopup(final BrowserContext context, final Page sourcePage, final Locator locator) {
		locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));

		try {
			final Page popup = context.waitForPage(
					() -> locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS)),
					new BrowserContext.WaitForPageOptions().setTimeout(8000));
			waitForUi(popup);
			return popup;
		} catch (final PlaywrightException e) {
			waitForUi(sourcePage);
			return null;
		}
	}

	private void selectGoogleAccountIfPrompted(final BrowserContext context, final Page appPage, final Page popupFromLogin) {
		final List<Page> candidatePages = new ArrayList<>();
		if (popupFromLogin != null) {
			candidatePages.add(popupFromLogin);
		}
		candidatePages.addAll(context.pages());

		for (int attempt = 0; attempt < 20; attempt++) {
			for (final Page candidate : candidatePages) {
				if (candidate == null || candidate.isClosed()) {
					continue;
				}

				final Locator accountText = findAnyVisible(candidate, candidate.getByText(GOOGLE_ACCOUNT_EMAIL),
						candidate.getByRole(AriaRole.BUTTON,
								new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(GOOGLE_ACCOUNT_EMAIL), Pattern.CASE_INSENSITIVE))));

				if (accountText != null) {
					clickAndWait(candidate, accountText);
					waitForUi(appPage);
					return;
				}
			}

			appPage.waitForTimeout(500);
		}
	}

	private void clickByVisibleText(final Page page, final String text) {
		final Locator locator = waitForAnyVisible(page, "visible text '" + text + "'",
				page.getByText(text, new Page.GetByTextOptions().setExact(true)),
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE))),
				page.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE))),
				page.getByText(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE)));

		clickAndWait(page, locator);
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
		locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUi(page);
	}

	private boolean isTextVisible(final Page page, final String text) {
		final Locator byExactText = page.getByText(text, new Page.GetByTextOptions().setExact(true));
		try {
			if (byExactText.count() > 0 && byExactText.first().isVisible()) {
				return true;
			}
		} catch (final PlaywrightException ignored) {
			// no-op
		}

		final Locator byRegexText = page.getByText(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE));
		try {
			return byRegexText.count() > 0 && byRegexText.first().isVisible();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void assertTextVisible(final Page page, final String text) {
		final Locator locator = waitForAnyVisible(page, "text '" + text + "'",
				page.getByText(text, new Page.GetByTextOptions().setExact(true)),
				page.getByRole(AriaRole.HEADING,
						new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE))),
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE))),
				page.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE))),
				page.getByText(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE)));

		assertTrue("Expected text to be visible: " + text, locator.isVisible());
	}

	private Locator waitForAnyVisible(final Page page, final String description, final Locator... locators) {
		final long deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS;
		Locator visible = findAnyVisible(page, locators);

		while (visible == null && System.currentTimeMillis() < deadline) {
			page.waitForTimeout(300);
			visible = findAnyVisible(page, locators);
		}

		if (visible == null) {
			throw new AssertionError("Unable to find visible element: " + description);
		}

		return visible;
	}

	private Locator findAnyVisible(final Page page, final Locator... locators) {
		Objects.requireNonNull(page, "page");

		for (final Locator locator : locators) {
			if (locator == null) {
				continue;
			}

			try {
				if (locator.count() > 0 && locator.first().isVisible()) {
					return locator.first();
				}
			} catch (final PlaywrightException ignored) {
				// try next locator candidate
			}
		}

		return null;
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (final PlaywrightException ignored) {
			// DOM content may already be loaded; continue with short settle wait.
		}

		page.waitForTimeout(800);
	}

	private void screenshot(final Page page, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName)).setFullPage(fullPage));
	}

	private void runStep(final String field, final StepExecutable executable) {
		try {
			final String details = executable.run();
			report.put(field, new StepResult(true, details == null ? "PASS" : details));
		} catch (final Throwable t) {
			final String message = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
			report.put(field, new StepResult(false, message));
		}
	}

	private void completeMissingReportEntries() {
		for (final String field : REPORT_FIELDS) {
			report.putIfAbsent(field, new StepResult(false, "Not executed."));
		}
	}

	private void printReport() {
		System.out.println("=== SaleADS Mi Negocio Workflow Report ===");
		for (final String field : REPORT_FIELDS) {
			final StepResult result = report.get(field);
			final String status = result.passed ? "PASS" : "FAIL";
			System.out.println(field + ": " + status + " - " + result.details);
		}
		System.out.println("Evidence directory: " + evidenceDir);
	}

	private void assertAllStepsPassed() {
		final List<String> failedSteps = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			if (!report.get(field).passed) {
				failedSteps.add(field);
			}
		}

		if (!failedSteps.isEmpty()) {
			fail("One or more workflow validations failed: " + failedSteps);
		}
	}

	@FunctionalInterface
	private interface StepExecutable {
		String run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}
	}
}
