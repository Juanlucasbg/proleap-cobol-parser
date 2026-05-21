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
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final long UI_TIMEOUT_MS = 20000;
	private static final long POPUP_TIMEOUT_MS = 10000;
	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to the login page of the current SaleADS environment.",
				loginUrl != null && !loginUrl.isBlank());

		final Path evidenceDir = createEvidenceDirectory();
		final LinkedHashMap<String, StepResult> report = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = launchBrowser(playwright);
			try (BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 900))) {
				final Page appPage = context.newPage();
				appPage.navigate(loginUrl);
				waitForUiLoad(appPage);

				runStep(report, "Login", () -> {
					loginWithGoogle(appPage);
					assertVisible(findSidebar(appPage), "Left sidebar navigation is not visible.");
					captureScreenshot(appPage, evidenceDir.resolve("step-1-dashboard.png"), true);
					return "Dashboard loaded and sidebar visible.";
				});

				runStep(report, "Mi Negocio menu", () -> {
					openMiNegocioMenu(appPage);
					assertVisible(findByText(appPage, "(?i)Agregar\\s+Negocio"),
							"'Agregar Negocio' option is not visible.");
					assertVisible(findByText(appPage, "(?i)Administrar\\s+Negocios"),
							"'Administrar Negocios' option is not visible.");
					captureScreenshot(appPage, evidenceDir.resolve("step-2-mi-negocio-expanded.png"), false);
					return "Mi Negocio submenu expanded successfully.";
				});

				runStep(report, "Agregar Negocio modal", () -> {
					clickByText(appPage, "(?i)Agregar\\s+Negocio");
					assertVisible(findByText(appPage, "(?i)Crear\\s+Nuevo\\s+Negocio"),
							"Modal title 'Crear Nuevo Negocio' was not found.");
					assertVisible(findByText(appPage, "(?i)Nombre\\s+del\\s+Negocio"),
							"Input label 'Nombre del Negocio' was not found.");
					assertVisible(findByText(appPage, "(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios"),
							"Usage text 'Tienes 2 de 3 negocios' was not found.");
					assertVisible(findByText(appPage, "(?i)Cancelar"), "'Cancelar' button was not found.");
					assertVisible(findByText(appPage, "(?i)Crear\\s+Negocio"), "'Crear Negocio' button was not found.");

					final Locator businessNameInput = findByLabel(appPage, "(?i)Nombre\\s+del\\s+Negocio");
					businessNameInput.fill("Negocio Prueba Automatizacion");
					waitForUiLoad(appPage);
					captureScreenshot(appPage, evidenceDir.resolve("step-3-agregar-negocio-modal.png"), false);
					clickByText(appPage, "(?i)Cancelar");
					return "Modal validated and closed via 'Cancelar'.";
				});

				runStep(report, "Administrar Negocios view", () -> {
					if (!isVisible(findByText(appPage, "(?i)Administrar\\s+Negocios"), 1500)) {
						openMiNegocioMenu(appPage);
					}
					clickByText(appPage, "(?i)Administrar\\s+Negocios");
					assertVisible(findByText(appPage, "(?i)Informaci[o\\u00f3]n\\s+General"),
							"'Informacion General' section not found.");
					assertVisible(findByText(appPage, "(?i)Detalles\\s+de\\s+la\\s+Cuenta"),
							"'Detalles de la Cuenta' section not found.");
					assertVisible(findByText(appPage, "(?i)Tus\\s+Negocios"), "'Tus Negocios' section not found.");
					assertVisible(findByText(appPage, "(?i)Secci[o\\u00f3]n\\s+Legal"),
							"'Seccion Legal' section not found.");
					captureScreenshot(appPage, evidenceDir.resolve("step-4-administrar-negocios.png"), true);
					return "Account page sections are visible.";
				});

				runStep(report, "Informaci\u00f3n General", () -> {
					final Locator infoSection = findSectionByHeading(appPage, "(?i)Informaci[o\\u00f3]n\\s+General");
					final String infoText = infoSection.innerText();
					Assert.assertTrue("User email is not visible in 'Informacion General'.",
							Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}").matcher(infoText).find());
					Assert.assertTrue("User name is not clearly visible in 'Informacion General'.", hasLikelyName(infoText));
					Assert.assertTrue("'BUSINESS PLAN' is not visible in 'Informacion General'.",
							Pattern.compile("(?i)BUSINESS\\s+PLAN").matcher(infoText).find());
					assertVisible(findByText(infoSection, "(?i)Cambiar\\s+Plan"),
							"'Cambiar Plan' button is not visible.");
					return "User details, plan, and action button verified.";
				});

				runStep(report, "Detalles de la Cuenta", () -> {
					final Locator accountDetailsSection = findSectionByHeading(appPage, "(?i)Detalles\\s+de\\s+la\\s+Cuenta");
					assertVisible(findByText(accountDetailsSection, "(?i)Cuenta\\s+creada"),
							"'Cuenta creada' is not visible.");
					assertVisible(findByText(accountDetailsSection, "(?i)Estado\\s+activo"),
							"'Estado activo' is not visible.");
					assertVisible(findByText(accountDetailsSection, "(?i)Idioma\\s+seleccionado"),
							"'Idioma seleccionado' is not visible.");
					return "Account detail labels are visible.";
				});

				runStep(report, "Tus Negocios", () -> {
					final Locator businessesSection = findSectionByHeading(appPage, "(?i)Tus\\s+Negocios");
					assertVisible(findByText(businessesSection, "(?i)Agregar\\s+Negocio"),
							"'Agregar Negocio' button is not visible in 'Tus Negocios'.");
					assertVisible(findByText(businessesSection, "(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios"),
							"'Tienes 2 de 3 negocios' is not visible in 'Tus Negocios'.");
					final String businessesText = businessesSection.innerText();
					Assert.assertTrue("Business list appears empty in 'Tus Negocios'.", businessesText.split("\\R").length >= 3);
					return "Business list and quota indicators validated.";
				});

				runStep(report, "T\u00e9rminos y Condiciones", () -> {
					final String finalUrl = validateLegalLink(appPage, "(?i)T[\\u00e9e]rminos\\s+y\\s+Condiciones",
							"(?i)T[\\u00e9e]rminos\\s+y\\s+Condiciones",
							evidenceDir.resolve("step-8-terminos-y-condiciones.png"));
					return "Validated legal page URL: " + finalUrl;
				});

				runStep(report, "Pol\u00edtica de Privacidad", () -> {
					final String finalUrl = validateLegalLink(appPage, "(?i)Pol[\\u00edi]tica\\s+de\\s+Privacidad",
							"(?i)Pol[\\u00edi]tica\\s+de\\s+Privacidad",
							evidenceDir.resolve("step-9-politica-de-privacidad.png"));
					return "Validated legal page URL: " + finalUrl;
				});
			} finally {
				browser.close();
			}
		}

		writeFinalReport(report, evidenceDir.resolve("step-10-final-report.txt"));
		assertAllPassed(report);
	}

	private Browser launchBrowser(final Playwright playwright) {
		final String browserName = Optional.ofNullable(System.getenv("SALEADS_BROWSER")).orElse("chromium")
				.toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(Optional.ofNullable(System.getenv("SALEADS_HEADLESS")).orElse("true"));
		final BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(headless);

		switch (browserName) {
		case "firefox":
			return playwright.firefox().launch(options);
		case "webkit":
			return playwright.webkit().launch(options);
		default:
			return playwright.chromium().launch(options);
		}
	}

	private void loginWithGoogle(final Page appPage) {
		final Locator loginButton = firstVisible(
				findByText(appPage, "(?i)Sign\\s*in\\s*with\\s*Google"),
				findByText(appPage, "(?i)Iniciar\\s+sesi[o\\u00f3]n\\s+con\\s+Google"),
				findByText(appPage, "(?i)Continuar\\s+con\\s+Google"),
				findByRoleWithName(appPage, AriaRole.BUTTON,
						"(?i)(Sign\\s*in\\s*with\\s*Google|Iniciar\\s+sesi[o\\u00f3]n\\s+con\\s+Google|Continuar\\s+con\\s+Google)"),
				findByRoleWithName(appPage, AriaRole.LINK,
						"(?i)(Sign\\s*in\\s*with\\s*Google|Iniciar\\s+sesi[o\\u00f3]n\\s+con\\s+Google|Continuar\\s+con\\s+Google)"));
		assertVisible(loginButton, "Google login button was not found.");

		Page popup = null;
		try {
			popup = appPage.waitForPopup(
					() -> loginButton.click(new Locator.ClickOptions().setTimeout(UI_TIMEOUT_MS)),
					new Page.WaitForPopupOptions().setTimeout(POPUP_TIMEOUT_MS));
		} catch (PlaywrightException ignored) {
			// Login may continue in the same tab.
		}
		waitForUiLoad(appPage);

		if (popup != null) {
			waitForUiLoad(popup);
			selectGoogleAccountIfVisible(popup);
		}
		selectGoogleAccountIfVisible(appPage);
		waitForUiLoad(appPage);
	}

	private void selectGoogleAccountIfVisible(final Page page) {
		final Locator accountOption = findByText(page, Pattern.quote(GOOGLE_ACCOUNT));
		if (isVisible(accountOption, 5000)) {
			accountOption.click(new Locator.ClickOptions().setTimeout(UI_TIMEOUT_MS));
			waitForUiLoad(page);
		}
	}

	private void openMiNegocioMenu(final Page appPage) {
		final Locator negocioSection = firstVisible(
				findByRoleWithName(appPage, AriaRole.LINK, "(?i)Negocio"),
				findByRoleWithName(appPage, AriaRole.BUTTON, "(?i)Negocio"),
				findByText(appPage, "(?i)Negocio"));
		assertVisible(negocioSection, "Sidebar section 'Negocio' was not found.");
		clickAndWait(appPage, negocioSection);

		final Locator miNegocioOption = firstVisible(
				findByRoleWithName(appPage, AriaRole.LINK, "(?i)Mi\\s+Negocio"),
				findByRoleWithName(appPage, AriaRole.BUTTON, "(?i)Mi\\s+Negocio"),
				findByText(appPage, "(?i)Mi\\s+Negocio"));
		assertVisible(miNegocioOption, "Option 'Mi Negocio' was not found.");
		clickAndWait(appPage, miNegocioOption);
	}

	private String validateLegalLink(final Page appPage, final String linkRegex, final String headingRegex,
			final Path screenshotPath) {
		final Locator legalSection = findSectionByHeading(appPage, "(?i)Secci[o\\u00f3]n\\s+Legal");
		final Locator legalLink = firstVisible(
				findByRoleWithName(legalSection, AriaRole.LINK, linkRegex),
				findByRoleWithName(legalSection, AriaRole.BUTTON, linkRegex),
				findByText(legalSection, linkRegex));
		assertVisible(legalLink, "Legal link not found for pattern: " + linkRegex);

		Page legalPage = null;
		boolean openedNewTab = false;
		try {
			legalPage = appPage.waitForPopup(
					() -> legalLink.click(new Locator.ClickOptions().setTimeout(UI_TIMEOUT_MS)),
					new Page.WaitForPopupOptions().setTimeout(POPUP_TIMEOUT_MS));
			openedNewTab = true;
		} catch (PlaywrightException ignored) {
			// Link may navigate in the same tab.
		}
		waitForUiLoad(appPage);
		if (legalPage == null) {
			legalPage = appPage;
		}

		waitForUiLoad(legalPage);
		assertVisible(findByText(legalPage, headingRegex), "Expected heading not found in legal page: " + headingRegex);
		final String legalText = legalPage.locator("body").innerText().trim();
		Assert.assertTrue("Legal page text appears too short.", legalText.length() > 200);
		captureScreenshot(legalPage, screenshotPath, true);
		final String finalUrl = legalPage.url();

		if (openedNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else if (!Pattern.compile("(?i)mi\\s+negocio").matcher(appPage.locator("body").innerText()).find()) {
			appPage.goBack();
			waitForUiLoad(appPage);
		}

		return finalUrl;
	}

	private void runStep(final Map<String, StepResult> report, final String stepName, final StepRunner runner) {
		try {
			final String detail = runner.run();
			report.put(stepName, new StepResult(true, detail));
		} catch (Throwable t) {
			report.put(stepName, new StepResult(false, t.getMessage()));
		}
	}

	private void writeFinalReport(final Map<String, StepResult> report, final Path reportPath) throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("saleads_mi_negocio_full_test final report").append(System.lineSeparator());
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ")
					.append(entry.getValue().passed ? "PASS" : "FAIL")
					.append(" | ").append(Optional.ofNullable(entry.getValue().detail).orElse(""))
					.append(System.lineSeparator());
		}
		Files.createDirectories(reportPath.getParent());
		Files.writeString(reportPath, builder.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		System.out.println(builder);
	}

	private void assertAllPassed(final Map<String, StepResult> report) {
		final StringBuilder failures = new StringBuilder();
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!entry.getValue().passed) {
				failures.append(System.lineSeparator()).append(entry.getKey()).append(": ").append(entry.getValue().detail);
			}
		}
		if (failures.length() > 0) {
			Assert.fail("One or more workflow checks failed:" + failures);
		}
	}

	private Locator findSidebar(final Page page) {
		return firstVisible(page.locator("aside"), findByText(page, "(?i)Negocio"), page.getByRole(AriaRole.NAVIGATION));
	}

	private Locator findSectionByHeading(final Page page, final String headingRegex) {
		final Locator heading = firstVisible(findByRoleWithName(page, AriaRole.HEADING, headingRegex),
				findByText(page, headingRegex));
		assertVisible(heading, "Section heading not found: " + headingRegex);
		return heading.locator("xpath=ancestor::*[self::section or self::div][1]");
	}

	private Locator findByText(final Page page, final String regex) {
		return page.getByText(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)).first();
	}

	private Locator findByText(final Locator scope, final String regex) {
		return scope.getByText(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)).first();
	}

	private Locator findByRoleWithName(final Page page, final AriaRole role, final String regex) {
		return page.getByRole(role, new Page.GetByRoleOptions()
				.setName(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))).first();
	}

	private Locator findByRoleWithName(final Locator scope, final AriaRole role, final String regex) {
		return scope.getByRole(role, new Locator.GetByRoleOptions()
				.setName(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))).first();
	}

	private Locator findByLabel(final Page page, final String regex) {
		return page.getByLabel(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)).first();
	}

	private Locator firstVisible(final Locator... locators) {
		for (Locator locator : locators) {
			if (isVisible(locator, 1500)) {
				return locator;
			}
		}
		return locators[0];
	}

	private void clickByText(final Page page, final String regex) {
		final Locator target = firstVisible(
				findByRoleWithName(page, AriaRole.BUTTON, regex),
				findByRoleWithName(page, AriaRole.LINK, regex),
				findByText(page, regex));
		assertVisible(target, "Could not find clickable element for text: " + regex);
		clickAndWait(page, target);
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.click(new Locator.ClickOptions().setTimeout(UI_TIMEOUT_MS));
		waitForUiLoad(page);
	}

	private void waitForUiLoad(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(UI_TIMEOUT_MS));
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (PlaywrightException ignored) {
			// Some pages keep long-lived connections and may never be fully idle.
		}
		page.waitForTimeout(500);
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private void assertVisible(final Locator locator, final String message) {
		if (!isVisible(locator, (int) UI_TIMEOUT_MS)) {
			throw new AssertionError(message);
		}
	}

	private boolean hasLikelyName(final String sectionText) {
		final String[] lines = sectionText.split("\\R");
		for (String line : lines) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (trimmed.contains("@")) {
				continue;
			}
			if (Pattern.compile("(?i)(Informaci[o\\u00f3]n\\s+General|BUSINESS\\s+PLAN|Cambiar\\s+Plan)").matcher(trimmed)
					.find()) {
				continue;
			}
			if (Pattern.compile("(?i)[A-Za-z]{2,}(\\s+[A-Za-z]{2,})+").matcher(trimmed).find()) {
				return true;
			}
		}
		return false;
	}

	private void captureScreenshot(final Page page, final Path path, final boolean fullPage) throws IOException {
		Files.createDirectories(path.getParent());
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path evidenceDir = Path.of("target", "evidence", "saleads-mi-negocio", timestamp);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	@FunctionalInterface
	private interface StepRunner {
		String run() throws Exception;
	}

	private static class StepResult {
		private final boolean passed;
		private final String detail;

		private StepResult(final boolean passed, final String detail) {
			this.passed = passed;
			this.detail = detail;
		}
	}
}
