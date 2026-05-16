package io.proleap.saleads.e2e;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String RUN_FLAG_ENV = "SALEADS_RUN_E2E";
	private static final String LOGIN_URL_ENV = "SALEADS_LOGIN_URL";
	private static final String HEADLESS_ENV = "SALEADS_HEADLESS";
	private static final String EXPECTED_NAME_ENV = "SALEADS_EXPECTED_USER_NAME";
	private static final String EXPECTED_EMAIL_ENV = "SALEADS_EXPECTED_USER_EMAIL";
	private static final String DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final int DEFAULT_TIMEOUT_MS = 15000;
	private static final int SHORT_TIMEOUT_MS = 6000;

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		Assume.assumeTrue("Set SALEADS_RUN_E2E=true to enable this e2e test.",
				"true".equalsIgnoreCase(System.getenv(RUN_FLAG_ENV)));

		final String loginUrl = System.getenv(LOGIN_URL_ENV);
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to the SaleADS login page URL for your target environment.",
				loginUrl != null && !loginUrl.isBlank());

		final LinkedHashMap<String, Boolean> results = createDefaultResultMap();
		final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<String, String>();
		final List<String> failures = new ArrayList<String>();
		final Path artifactsDir = createArtifactsDirectory();
		final String expectedName = getOptionalEnv(EXPECTED_NAME_ENV);
		final String expectedEmail = getOptionalEnv(EXPECTED_EMAIL_ENV) != null ? getOptionalEnv(EXPECTED_EMAIL_ENV)
				: DEFAULT_ACCOUNT_EMAIL;

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(isHeadless()));
			final BrowserContext context = browser.newContext(
					new Browser.NewContextOptions().setViewportSize(1600, 1000));
			final Page appPage = context.newPage();

			appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUiLoad(appPage);

			// Step 1: Login with Google and validate main app shell.
			final boolean loginOk = performGoogleLogin(context, appPage) && validatePostLoginShell(appPage);
			recordStepResult(results, failures, "Login", loginOk,
					"Google login did not land on the main application interface with sidebar visible.");
			takeScreenshot(appPage, artifactsDir, "01-dashboard-loaded.png", false);

			// Step 2: Open Mi Negocio menu and validate submenu entries.
			openMiNegocioMenu(appPage);
			final boolean miNegocioMenuOk = isAnyVisible(appPage,
					byText(appPage, "(?i)Agregar\\s+Negocio"),
					byText(appPage, "(?i)Administrar\\s+Negocios"));
			recordStepResult(results, failures, "Mi Negocio menu", miNegocioMenuOk,
					"Mi Negocio submenu did not expose both 'Agregar Negocio' and 'Administrar Negocios'.");
			takeScreenshot(appPage, artifactsDir, "02-mi-negocio-menu-expanded.png", false);

			// Step 3: Validate Agregar Negocio modal.
			openAgregarNegocio(appPage);
			final boolean agregarModalOk = isAnyVisible(appPage, byText(appPage, "(?i)Crear\\s+Nuevo\\s+Negocio"))
					&& isAnyVisible(appPage,
							appPage.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions()
									.setName(Pattern.compile("(?i)Nombre\\s+del\\s+Negocio"))),
							byText(appPage, "(?i)Nombre\\s+del\\s+Negocio"))
					&& isAnyVisible(appPage, byText(appPage, "(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios"))
					&& isAnyVisible(appPage,
							appPage.getByRole(AriaRole.BUTTON,
									new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Cancelar"))),
							appPage.getByRole(AriaRole.BUTTON,
									new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Crear\\s+Negocio"))));

			takeScreenshot(appPage, artifactsDir, "03-agregar-negocio-modal.png", false);
			fillAndCancelAgregarNegocioModal(appPage);
			recordStepResult(results, failures, "Agregar Negocio modal", agregarModalOk,
					"Agregar Negocio modal did not include the expected title, fields, quota, and buttons.");

			// Step 4: Open Administrar Negocios and validate account page sections.
			openAdministrarNegocios(appPage);
			final boolean administrarNegociosOk = isAnyVisible(appPage, byText(appPage, "(?i)Información\\s+General"))
					&& isAnyVisible(appPage, byText(appPage, "(?i)Detalles\\s+de\\s+la\\s+Cuenta"))
					&& isAnyVisible(appPage, byText(appPage, "(?i)Tus\\s+Negocios"))
					&& isAnyVisible(appPage, byText(appPage, "(?i)Sección\\s+Legal"));
			recordStepResult(results, failures, "Administrar Negocios view", administrarNegociosOk,
					"Administrar Negocios page is missing one or more required sections.");
			takeScreenshot(appPage, artifactsDir, "04-administrar-negocios-full.png", true);

			// Step 5: Validate Información General section.
			final boolean informacionGeneralOk = validateInformacionGeneral(appPage, expectedName, expectedEmail);
			recordStepResult(results, failures, "Información General", informacionGeneralOk,
					"Información General is missing expected user/profile or plan details.");

			// Step 6: Validate Detalles de la Cuenta section.
			final boolean detallesCuentaOk = isAnyVisible(appPage, byText(appPage, "(?i)Cuenta\\s+creada"))
					&& isAnyVisible(appPage, byText(appPage, "(?i)Estado\\s+activo"))
					&& isAnyVisible(appPage, byText(appPage, "(?i)Idioma\\s+seleccionado"));
			recordStepResult(results, failures, "Detalles de la Cuenta", detallesCuentaOk,
					"Detalles de la Cuenta does not show all required fields.");

			// Step 7: Validate Tus Negocios section.
			final boolean tusNegociosOk = isAnyVisible(appPage, byText(appPage, "(?i)Tus\\s+Negocios"))
					&& isAnyVisible(appPage,
							appPage.getByRole(AriaRole.BUTTON,
									new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Agregar\\s+Negocio"))),
							appPage.getByRole(AriaRole.LINK,
									new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Agregar\\s+Negocio"))))
					&& isAnyVisible(appPage, byText(appPage, "(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios"))
					&& hasBusinessListContent(appPage);
			recordStepResult(results, failures, "Tus Negocios", tusNegociosOk,
					"Tus Negocios section is missing business list content or required controls.");

			// Step 8: Validate Términos y Condiciones.
			final LegalValidationResult termsResult = validateLegalLink(context, appPage, "Términos y Condiciones",
					"Términos y Condiciones", artifactsDir, "05-terminos-y-condiciones.png");
			recordStepResult(results, failures, "Términos y Condiciones", termsResult.valid,
					"Términos y Condiciones content was not validated.");
			legalUrls.put("Términos y Condiciones", termsResult.finalUrl);

			// Step 9: Validate Política de Privacidad.
			final LegalValidationResult privacyResult = validateLegalLink(context, appPage, "Política de Privacidad",
					"Política de Privacidad", artifactsDir, "06-politica-de-privacidad.png");
			recordStepResult(results, failures, "Política de Privacidad", privacyResult.valid,
					"Política de Privacidad content was not validated.");
			legalUrls.put("Política de Privacidad", privacyResult.finalUrl);
		}

		writeFinalReport(results, legalUrls, failures, artifactsDir);
		Assert.assertTrue("One or more SaleADS Mi Negocio validations failed:\n" + String.join("\n", failures),
				failures.isEmpty());
	}

	private LinkedHashMap<String, Boolean> createDefaultResultMap() {
		final LinkedHashMap<String, Boolean> result = new LinkedHashMap<String, Boolean>();
		result.put("Login", false);
		result.put("Mi Negocio menu", false);
		result.put("Agregar Negocio modal", false);
		result.put("Administrar Negocios view", false);
		result.put("Información General", false);
		result.put("Detalles de la Cuenta", false);
		result.put("Tus Negocios", false);
		result.put("Términos y Condiciones", false);
		result.put("Política de Privacidad", false);
		return result;
	}

	private void openMiNegocioMenu(final Page page) {
		final Locator negocioSection = firstVisible(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Negocio$"))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Negocio$"))),
				byText(page, "(?i)^Negocio$"));
		if (negocioSection != null) {
			clickAndWait(page, negocioSection);
		}

		final Locator miNegocioOption = firstVisible(page,
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Mi\\s+Negocio$"))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Mi\\s+Negocio$"))),
				byText(page, "(?i)^Mi\\s+Negocio$"));
		if (miNegocioOption != null) {
			clickAndWait(page, miNegocioOption);
		}
	}

	private void openAgregarNegocio(final Page page) {
		final Locator agregarNegocio = firstVisible(page,
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Agregar\\s+Negocio$"))),
				page.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Agregar\\s+Negocio$"))),
				byText(page, "(?i)^Agregar\\s+Negocio$"));
		if (agregarNegocio != null) {
			clickAndWait(page, agregarNegocio);
		}
	}

	private void fillAndCancelAgregarNegocioModal(final Page page) {
		final Locator nombreNegocioInput = firstVisible(page,
				page.getByRole(AriaRole.TEXTBOX,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Nombre\\s+del\\s+Negocio"))),
				byText(page, "(?i)Nombre\\s+del\\s+Negocio"));

		if (nombreNegocioInput != null) {
			nombreNegocioInput.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
			waitForUiLoad(page);
			nombreNegocioInput.fill("Negocio Prueba Automatizacion");
			waitForUiLoad(page);
		}

		final Locator cancelarButton = firstVisible(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Cancelar$"))),
				byText(page, "(?i)^Cancelar$"));
		if (cancelarButton != null) {
			clickAndWait(page, cancelarButton);
		}
	}

	private void openAdministrarNegocios(final Page page) {
		openMiNegocioMenu(page);
		final Locator administrarNegocios = firstVisible(page,
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Administrar\\s+Negocios$"))),
				page.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Administrar\\s+Negocios$"))),
				byText(page, "(?i)^Administrar\\s+Negocios$"));
		if (administrarNegocios != null) {
			clickAndWait(page, administrarNegocios);
		}
	}

	private boolean performGoogleLogin(final BrowserContext context, final Page appPage) {
		final Locator loginButton = firstVisible(appPage,
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
						.setName(Pattern.compile("(?i)sign\\s*in\\s*with\\s*google|google|continuar\\s*con\\s*google"))),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
						.setName(Pattern.compile("(?i)sign\\s*in\\s*with\\s*google|google|continuar\\s*con\\s*google"))),
				byText(appPage, "(?i)sign\\s*in\\s*with\\s*google|continuar\\s*con\\s*google"));

		if (loginButton == null) {
			return false;
		}

		final int pagesBeforeClick = context.pages().size();
		clickAndWait(appPage, loginButton);

		Page authPage = waitForNewPage(context, pagesBeforeClick, 10000);
		if (authPage == null) {
			authPage = appPage;
		} else {
			waitForUiLoad(authPage);
		}

		final Locator accountOption = firstVisible(authPage,
				authPage.getByText(DEFAULT_ACCOUNT_EMAIL, new Page.GetByTextOptions().setExact(false)),
				authPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile(DEFAULT_ACCOUNT_EMAIL,
						Pattern.CASE_INSENSITIVE))),
				authPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile(DEFAULT_ACCOUNT_EMAIL,
						Pattern.CASE_INSENSITIVE))));

		if (accountOption != null) {
			clickAndWait(authPage, accountOption);
		}

		if (authPage != appPage) {
			waitForUiLoad(appPage);
			appPage.bringToFront();
		}

		return true;
	}

	private boolean validatePostLoginShell(final Page page) {
		final boolean hasMainInterface = isAnyVisible(page, page.locator("main"), page.locator("[role='main']"));
		final boolean hasSidebarNavigation = isAnyVisible(page, page.locator("aside"), page.locator("nav"),
				byText(page, "(?i)^Negocio$"));
		return hasMainInterface && hasSidebarNavigation;
	}

	private boolean validateInformacionGeneral(final Page page, final String expectedName, final String expectedEmail) {
		final boolean userNameVisible;
		if (expectedName != null) {
			userNameVisible = isAnyVisible(page, page.getByText(expectedName, new Page.GetByTextOptions().setExact(false)));
		} else {
			userNameVisible = isAnyVisible(page, byText(page, "(?i)Nombre"), page.locator("main h1"), page.locator("main h2"));
		}

		final boolean userEmailVisible = isAnyVisible(page,
				page.getByText(expectedEmail, new Page.GetByTextOptions().setExact(false)),
				page.getByText(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")));
		final boolean businessPlanVisible = isAnyVisible(page, byText(page, "(?i)BUSINESS\\s+PLAN"));
		final boolean cambiarPlanVisible = isAnyVisible(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Cambiar\\s+Plan"))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Cambiar\\s+Plan"))),
				byText(page, "(?i)Cambiar\\s+Plan"));
		return userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible;
	}

	private boolean hasBusinessListContent(final Page page) {
		final int listItems = page.locator("main li").count();
		final int tableRows = page.locator("main table tbody tr").count();
		final int cards = page.locator("main [class*='business']").count();
		return listItems > 0 || tableRows > 0 || cards > 0;
	}

	private LegalValidationResult validateLegalLink(final BrowserContext context, final Page appPage, final String linkText,
			final String expectedHeading, final Path artifactsDir, final String screenshotFile) {
		final Locator legalLink = firstVisible(appPage,
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(linkText)))),
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(linkText)))),
				byText(appPage, "(?i)" + Pattern.quote(linkText)));
		if (legalLink == null) {
			return new LegalValidationResult(false, "");
		}

		final String applicationUrlBeforeClick = appPage.url();
		final int pagesBeforeClick = context.pages().size();
		clickAndWait(appPage, legalLink);

		Page targetPage = waitForNewPage(context, pagesBeforeClick, 5000);
		if (targetPage == null) {
			targetPage = appPage;
		} else {
			waitForUiLoad(targetPage);
		}

		final boolean headingVisible = isAnyVisible(targetPage,
				targetPage.getByRole(AriaRole.HEADING,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(expectedHeading)))),
				byText(targetPage, "(?i)" + Pattern.quote(expectedHeading)));
		final String pageText = safeBodyText(targetPage);
		final boolean legalTextVisible = pageText != null && pageText.trim().length() > 200;
		takeScreenshot(targetPage, artifactsDir, screenshotFile, true);
		final String finalUrl = targetPage.url() != null ? targetPage.url() : "";

		if (targetPage != appPage) {
			try {
				targetPage.close();
			} catch (PlaywrightException ignored) {
			}
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else if (!applicationUrlBeforeClick.equals(appPage.url())) {
			try {
				appPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
				waitForUiLoad(appPage);
			} catch (PlaywrightException ignored) {
			}
		}

		return new LegalValidationResult(headingVisible && legalTextVisible, finalUrl);
	}

	private Locator byText(final Page page, final String regex) {
		return page.getByText(Pattern.compile(regex));
	}

	private Locator firstVisible(final Page page, final Locator... candidates) {
		for (final Locator candidate : candidates) {
			if (candidate == null) {
				continue;
			}
			try {
				candidate.first().waitFor(
						new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(SHORT_TIMEOUT_MS));
				return candidate.first();
			} catch (PlaywrightException ignored) {
			}
		}
		return null;
	}

	private boolean isAnyVisible(final Page page, final Locator... candidates) {
		return firstVisible(page, candidates) != null;
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.first().click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUiLoad(page);
	}

	private void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (PlaywrightException ignored) {
		}
		page.waitForTimeout(700);
	}

	private Page waitForNewPage(final BrowserContext context, final int pagesBefore, final int timeoutMs) {
		final long end = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < end) {
			final List<Page> pages = context.pages();
			if (pages.size() > pagesBefore) {
				return pages.get(pages.size() - 1);
			}
			try {
				Thread.sleep(200);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return null;
			}
		}
		return null;
	}

	private Path createArtifactsDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT));
		final Path artifactsDir = Paths.get("target", "saleads-e2e-artifacts", timestamp);
		Files.createDirectories(artifactsDir);
		return artifactsDir;
	}

	private void takeScreenshot(final Page page, final Path artifactsDir, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(artifactsDir.resolve(fileName)).setFullPage(fullPage));
	}

	private void recordStepResult(final Map<String, Boolean> results, final List<String> failures, final String fieldName,
			final boolean passed, final String failureMessage) {
		results.put(fieldName, Boolean.valueOf(passed));
		if (!passed) {
			failures.add(fieldName + ": " + failureMessage);
		}
	}

	private void writeFinalReport(final LinkedHashMap<String, Boolean> results, final LinkedHashMap<String, String> legalUrls,
			final List<String> failures, final Path artifactsDir) throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("saleads_mi_negocio_full_test").append('\n');
		report.append("artifacts_dir=").append(artifactsDir.toAbsolutePath()).append('\n').append('\n');
		report.append("Validation results:").append('\n');
		for (final Map.Entry<String, Boolean> entry : results.entrySet()) {
			report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().booleanValue() ? "PASS" : "FAIL")
					.append('\n');
		}
		report.append('\n');
		report.append("Final URLs:").append('\n');
		report.append("- Términos y Condiciones: ").append(legalUrls.getOrDefault("Términos y Condiciones", ""))
				.append('\n');
		report.append("- Política de Privacidad: ").append(legalUrls.getOrDefault("Política de Privacidad", "")).append('\n');
		report.append('\n');
		report.append("Failures:").append('\n');
		if (failures.isEmpty()) {
			report.append("- none").append('\n');
		} else {
			for (final String failure : failures) {
				report.append("- ").append(failure).append('\n');
			}
		}
		Files.write(artifactsDir.resolve("final-report.txt"), report.toString().getBytes(StandardCharsets.UTF_8));
	}

	private String safeBodyText(final Page page) {
		try {
			return page.textContent("body");
		} catch (PlaywrightException ignored) {
			return null;
		}
	}

	private boolean isHeadless() {
		final String headlessValue = System.getenv(HEADLESS_ENV);
		if (headlessValue == null || headlessValue.isBlank()) {
			return true;
		}
		return !"false".equalsIgnoreCase(headlessValue);
	}

	private String getOptionalEnv(final String key) {
		final String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			return null;
		}
		return value;
	}

	private static final class LegalValidationResult {
		private final boolean valid;
		private final String finalUrl;

		private LegalValidationResult(final boolean valid, final String finalUrl) {
			this.valid = valid;
			this.finalUrl = finalUrl;
		}
	}
}
