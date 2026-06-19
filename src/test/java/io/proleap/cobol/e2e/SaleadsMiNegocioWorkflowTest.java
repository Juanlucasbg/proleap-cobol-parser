package io.proleap.cobol.e2e;

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
import java.util.regex.Pattern;

import org.junit.Assert;
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
import com.microsoft.playwright.options.WaitUntilState;

public class SaleadsMiNegocioWorkflowTest {

	private static final long DEFAULT_TIMEOUT_MS = 15000;
	private static final long SHORT_TIMEOUT_MS = 2500;

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFORMACION_GENERAL = "Informacion General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Terminos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Politica de Privacidad";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		Assume.assumeTrue(
				"Set SALEADS_RUN_E2E=true to run this UI workflow.",
				Boolean.parseBoolean(env("SALEADS_RUN_E2E", "false")));

		final String loginUrl = env("SALEADS_LOGIN_URL", "");
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL to the login page of the active SaleADS environment.",
				!loginUrl.isBlank());

		final String googleEmail = env("SALEADS_GOOGLE_EMAIL", "juanlucasbarbiergarzon@gmail.com");
		final String expectedUserEmail = env("SALEADS_EXPECTED_USER_EMAIL", googleEmail);
		final String expectedUserName = env("SALEADS_EXPECTED_USER_NAME", "");
		final boolean headless = Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"));
		final Path evidenceDir = createEvidenceDirectory();

		final Map<String, Boolean> report = createDefaultReport();
		final List<String> errors = new ArrayList<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(headless);
			try (Browser browser = playwright.chromium().launch(launchOptions);
					BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
					Page page = context.newPage()) {

				page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
				page.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
				waitForUi(page);

				runStep(REPORT_LOGIN, report, errors, () -> {
					loginWithGoogle(page, context, googleEmail);
					waitForApplicationShell(page);
					screenshot(page, evidenceDir, "01-dashboard-loaded.png", false);
				});

				runStep(REPORT_MI_NEGOCIO_MENU, report, errors, () -> {
					openMiNegocioMenu(page);
					assertVisibleText(page, "Agregar Negocio");
					assertVisibleText(page, "Administrar Negocios");
					screenshot(page, evidenceDir, "02-mi-negocio-menu-expanded.png", false);
				});

				runStep(REPORT_AGREGAR_NEGOCIO_MODAL, report, errors, () -> {
					clickByVisibleText(page, "Agregar Negocio");
					assertVisibleText(page, "Crear Nuevo Negocio");
					Locator businessNameInput = findInputByLabelOrPlaceholder(page, "Nombre del Negocio");
					businessNameInput.click();
					assertVisibleText(page, "Tienes 2 de 3 negocios");
					assertVisibleText(page, "Cancelar");
					assertVisibleText(page, "Crear Negocio");
					screenshot(page, evidenceDir, "03-agregar-negocio-modal.png", false);
					businessNameInput.fill("Negocio Prueba Automatizacion");
					clickByVisibleText(page, "Cancelar");
				});

				runStep(REPORT_ADMINISTRAR_NEGOCIOS_VIEW, report, errors, () -> {
					openMiNegocioMenu(page);
					clickByVisibleText(page, "Administrar Negocios");
					assertVisibleText(page, "Informacion General");
					assertVisibleText(page, "Detalles de la Cuenta");
					assertVisibleText(page, "Tus Negocios");
					assertVisibleText(page, "Seccion Legal");
					screenshot(page, evidenceDir, "04-administrar-negocios-full.png", true);
				});

				runStep(REPORT_INFORMACION_GENERAL, report, errors, () -> {
					assertVisibleText(page, "Informacion General");
					assertVisibleText(page, "BUSINESS PLAN");
					assertVisibleText(page, "Cambiar Plan");
					assertPageContainsEmail(page, expectedUserEmail);
					assertUserNameVisible(page, expectedUserName, expectedUserEmail);
				});

				runStep(REPORT_DETALLES_CUENTA, report, errors, () -> {
					assertVisibleText(page, "Cuenta creada");
					assertVisibleText(page, "Estado activo");
					assertVisibleText(page, "Idioma seleccionado");
				});

				runStep(REPORT_TUS_NEGOCIOS, report, errors, () -> {
					assertVisibleText(page, "Tus Negocios");
					assertVisibleText(page, "Agregar Negocio");
					assertVisibleText(page, "Tienes 2 de 3 negocios");
					assertBusinessListVisible(page);
				});

				runStep(REPORT_TERMINOS, report, errors, () -> {
					LegalValidationResult result = validateLegalLink(page, context, "Terminos y Condiciones",
							"Terminos y Condiciones", evidenceDir, "05-terminos-y-condiciones.png");
					legalUrls.put(REPORT_TERMINOS, result.finalUrl);
				});

				runStep(REPORT_PRIVACIDAD, report, errors, () -> {
					LegalValidationResult result = validateLegalLink(page, context, "Politica de Privacidad",
							"Politica de Privacidad", evidenceDir, "06-politica-de-privacidad.png");
					legalUrls.put(REPORT_PRIVACIDAD, result.finalUrl);
				});
			}
		}

		printFinalReport(report, legalUrls, evidenceDir, errors);
		Assert.assertTrue("One or more SaleADS workflow validations failed:\n" + String.join("\n", errors),
				errors.isEmpty());
	}

	private void loginWithGoogle(final Page page, final BrowserContext context, final String googleEmail) {
		int pagesBefore = context.pages().size();
		Locator loginButton = firstVisible(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
						.setName(Pattern.compile("(?iu).*(sign in with google|iniciar sesion con google|google).*"))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
						.setName(Pattern.compile("(?iu).*(sign in with google|iniciar sesion con google|google).*"))),
				page.getByText(Pattern.compile("(?iu).*(sign in with google|iniciar sesion con google|google).*")));
		clickAndWait(page, loginButton);

		Page popup = waitForNewPage(context, pagesBefore, 6000);
		if (popup != null) {
			popup.bringToFront();
			waitForUi(popup);
			selectGoogleAccountIfVisible(popup, googleEmail);
			waitForUi(popup);
			page.bringToFront();
		}
		selectGoogleAccountIfVisible(page, googleEmail);
		waitForUi(page);
	}

	private void waitForApplicationShell(final Page page) {
		waitForAnyText(page, Arrays.asList("Negocio", "Mi Negocio", "Dashboard"));
		Locator sidebar = page.locator("aside, nav").first();
		sidebar.waitFor(
				new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
	}

	private void openMiNegocioMenu(final Page page) {
		try {
			clickByVisibleText(page, "Negocio");
		} catch (Throwable ignored) {
			// Some environments render Mi Negocio directly in the sidebar.
		}
		clickByVisibleText(page, "Mi Negocio");
		waitForUi(page);
	}

	private void selectGoogleAccountIfVisible(final Page page, final String googleEmail) {
		Locator accountLocator = page.getByText(Pattern.compile("(?iu).*" + Pattern.quote(googleEmail) + ".*")).first();
		try {
			accountLocator.waitFor(
					new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(SHORT_TIMEOUT_MS));
			clickAndWait(page, accountLocator);
		} catch (PlaywrightException ignored) {
			// Account chooser might not appear when auth session is already active.
		}
	}

	private LegalValidationResult validateLegalLink(final Page page, final BrowserContext context, final String linkText,
			final String headingText, final Path evidenceDir, final String screenshotFileName) {
		int pagesBefore = context.pages().size();
		String initialAppUrl = page.url();
		clickByVisibleText(page, linkText);

		Page destinationPage = waitForNewPage(context, pagesBefore, 6000);
		boolean openedInNewTab = destinationPage != null;
		if (!openedInNewTab) {
			destinationPage = page;
		}

		destinationPage.bringToFront();
		waitForUi(destinationPage);
		assertVisibleText(destinationPage, headingText);
		assertLegalContentVisible(destinationPage);
		screenshot(destinationPage, evidenceDir, screenshotFileName, true);
		String finalUrl = destinationPage.url();

		if (openedInNewTab) {
			destinationPage.close();
			page.bringToFront();
		} else if (!initialAppUrl.equals(page.url())) {
			try {
				page.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			} catch (PlaywrightException ignored) {
				// If back navigation is not available, continue with current page state.
			}
		}
		waitForUi(page);
		return new LegalValidationResult(finalUrl, openedInNewTab);
	}

	private void assertVisibleText(final Page page, final String text) {
		Locator locator = page.getByText(Pattern.compile("(?iu).*" + Pattern.quote(text) + ".*")).first();
		locator.waitFor(
				new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
	}

	private void assertPageContainsEmail(final Page page, final String expectedEmail) {
		Locator emailLocator = page.getByText(Pattern.compile("(?iu).*" + Pattern.quote(expectedEmail) + ".*")).first();
		emailLocator.waitFor(
				new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
	}

	private void assertUserNameVisible(final Page page, final String expectedUserName, final String expectedUserEmail) {
		if (!expectedUserName.isBlank()) {
			assertVisibleText(page, expectedUserName);
			return;
		}

		String bodyText = page.locator("body").innerText();
		Pattern emailPattern = Pattern.compile(Pattern.quote(expectedUserEmail), Pattern.CASE_INSENSITIVE);
		Pattern namePattern = Pattern.compile("(?m)^[\\p{L}][\\p{L}\\s'-]{2,}$");

		Assert.assertTrue("Expected user email was not found in Informacion General section.",
				emailPattern.matcher(bodyText).find());
		Assert.assertTrue("Could not identify a likely user name. Set SALEADS_EXPECTED_USER_NAME for strict validation.",
				namePattern.matcher(bodyText).find());
	}

	private void assertBusinessListVisible(final Page page) {
		Locator section = page.getByText(Pattern.compile("(?iu).*Tus Negocios.*")).first();
		section.waitFor(
				new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
		int entries = page.locator("li, [role='row'], table tr, .card, .business-item").count();
		Assert.assertTrue("No business list entries were detected in the Tus Negocios section.", entries > 0);
	}

	private void assertLegalContentVisible(final Page page) {
		String text = page.locator("body").innerText();
		Assert.assertTrue("Legal page content appears too short.", text != null && text.trim().length() > 120);
	}

	@SafeVarargs
	private final Locator firstVisible(final Page page, final Locator... options) {
		for (Locator option : options) {
			try {
				option.first().waitFor(
						new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(SHORT_TIMEOUT_MS));
				return option.first();
			} catch (PlaywrightException ignored) {
				// Continue searching for a visible option.
			}
		}
		throw new AssertionError("No visible locator found for the requested action on page: " + page.url());
	}

	private Locator findInputByLabelOrPlaceholder(final Page page, final String labelText) {
		Pattern labelPattern = Pattern.compile("(?iu).*" + Pattern.quote(labelText) + ".*");
		return firstVisible(page, page.getByLabel(labelPattern), page.locator("input[placeholder*='" + labelText + "']"),
				page.locator("input[name*='negocio']"));
	}

	private void clickByVisibleText(final Page page, final String text) {
		Pattern textPattern = Pattern.compile("(?iu).*" + Pattern.quote(text) + ".*");
		Locator locator = firstVisible(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(textPattern)),
				page.getByText(textPattern));
		clickAndWait(page, locator);
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED,
					new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (PlaywrightException ignored) {
			// DOMContentLoaded may already be reached.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (PlaywrightException ignored) {
			// Some SPAs keep network requests open. Continue after a short stabilization delay.
		}
		page.waitForTimeout(500);
	}

	private void waitForAnyText(final Page page, final List<String> texts) {
		PlaywrightException lastException = null;
		for (String text : texts) {
			try {
				assertVisibleText(page, text);
				return;
			} catch (PlaywrightException ex) {
				lastException = ex;
			}
		}
		if (lastException != null) {
			throw lastException;
		}
		throw new AssertionError("None of the expected texts became visible: " + texts);
	}

	private Page waitForNewPage(final BrowserContext context, final int pagesBeforeClick, final long timeoutMs) {
		long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start < timeoutMs) {
			List<Page> pages = context.pages();
			if (pages.size() > pagesBeforeClick) {
				Page newPage = pages.get(pages.size() - 1);
				try {
					newPage.waitForLoadState(LoadState.DOMCONTENTLOADED,
							new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
				} catch (PlaywrightException ignored) {
					// Continue, the new tab may still be rendering.
				}
				return newPage;
			}
			try {
				context.waitForCondition(() -> context.pages().size() > pagesBeforeClick,
						new BrowserContext.WaitForConditionOptions().setTimeout(500));
			} catch (PlaywrightException ignored) {
				// Poll until timeout to avoid failing on transient timing.
			}
		}
		return null;
	}

	private void screenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
		Path screenshotPath = evidenceDir.resolve(fileName);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	private Map<String, Boolean> createDefaultReport() {
		Map<String, Boolean> report = new LinkedHashMap<>();
		report.put(REPORT_LOGIN, false);
		report.put(REPORT_MI_NEGOCIO_MENU, false);
		report.put(REPORT_AGREGAR_NEGOCIO_MODAL, false);
		report.put(REPORT_ADMINISTRAR_NEGOCIOS_VIEW, false);
		report.put(REPORT_INFORMACION_GENERAL, false);
		report.put(REPORT_DETALLES_CUENTA, false);
		report.put(REPORT_TUS_NEGOCIOS, false);
		report.put(REPORT_TERMINOS, false);
		report.put(REPORT_PRIVACIDAD, false);
		return report;
	}

	private void runStep(final String reportField, final Map<String, Boolean> report, final List<String> errors,
			final CheckedRunnable stepBody) {
		try {
			stepBody.run();
			report.put(reportField, true);
		} catch (Throwable throwable) {
			report.put(reportField, false);
			errors.add(reportField + ": " + rootCauseMessage(throwable));
		}
	}

	private String rootCauseMessage(final Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getMessage() == null ? current.toString() : current.getMessage();
	}

	private void printFinalReport(final Map<String, Boolean> report, final Map<String, String> legalUrls,
			final Path evidenceDir, final List<String> errors) {
		System.out.println("=== SaleADS Mi Negocio Final Report ===");
		report.forEach((field, status) -> System.out.println(field + ": " + (status ? "PASS" : "FAIL")));
		legalUrls.forEach((name, url) -> System.out.println(name + " URL: " + url));
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		if (!errors.isEmpty()) {
			System.out.println("Failure details:");
			errors.forEach(error -> System.out.println(" - " + error));
		}
	}

	private Path createEvidenceDirectory() throws Exception {
		String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		Path evidenceDir = Paths.get("target", "saleads-evidence", runId);
		return Files.createDirectories(evidenceDir);
	}

	private String env(final String name, final String fallback) {
		String value = System.getenv(name);
		return value == null ? fallback : value.trim();
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class LegalValidationResult {
		private final String finalUrl;
		@SuppressWarnings("unused")
		private final boolean openedInNewTab;

		private LegalValidationResult(final String finalUrl, final boolean openedInNewTab) {
			this.finalUrl = finalUrl;
			this.openedInNewTab = openedInNewTab;
		}
	}
}
