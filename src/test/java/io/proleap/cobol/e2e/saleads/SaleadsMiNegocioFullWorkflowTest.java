package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
import com.microsoft.playwright.options.WaitUntilState;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final int DEFAULT_TIMEOUT_MS = 20000;

	@Test
	public void fullMiNegocioWorkflow() throws Exception {
		final String startUrl = getSetting("saleads.start.url", "SALEADS_START_URL");
		Assume.assumeTrue("Set saleads.start.url or SALEADS_START_URL before running this test.",
				startUrl != null && !startUrl.isBlank());

		final String googleAccount = getSetting("saleads.google.account", "SALEADS_GOOGLE_ACCOUNT");
		final String accountToUse = googleAccount == null || googleAccount.isBlank() ? DEFAULT_GOOGLE_ACCOUNT : googleAccount;
		final boolean headless = Boolean.parseBoolean(getSettingOrDefault("saleads.headless", "SALEADS_HEADLESS", "true"));
		final int timeoutMs = parseIntOrDefault(getSettingOrDefault("saleads.timeout.ms", "SALEADS_TIMEOUT_MS",
				String.valueOf(DEFAULT_TIMEOUT_MS)), DEFAULT_TIMEOUT_MS);
		final int slowMoMs = parseIntOrDefault(getSettingOrDefault("saleads.slowmo.ms", "SALEADS_SLOWMO_MS", "0"), 0);

		final Path evidenceDir = createEvidenceDir();
		final LinkedHashMap<String, String> report = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();
		final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();

		final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(headless);
		if (slowMoMs > 0) {
			launchOptions.setSlowMo((double) slowMoMs);
		}

		try (Playwright playwright = Playwright.create();
				Browser browser = playwright.chromium().launch(launchOptions);
				BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080)
						.setIgnoreHTTPSErrors(true).setAcceptDownloads(true))) {
			final Page page = context.newPage();
			page.navigate(startUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
					.setTimeout((double) timeoutMs));
			waitForUiLoad(page, timeoutMs);

			final boolean loginOk = runStep("Login", report, failures, () -> {
				final Page popup = clickForOptionalPopup(page, context, timeoutMs, "Sign in with Google", "Login with Google",
						"Iniciar sesión con Google", "Continuar con Google", "Google");
				final Page activeAuthPage = popup == null ? page : popup;
				selectGoogleAccountIfShown(activeAuthPage, accountToUse, timeoutMs);

				if (popup != null) {
					try {
						popup.waitForClose(new Page.WaitForCloseOptions().setTimeout((double) timeoutMs));
					} catch (final PlaywrightException ignored) {
						// Some environments keep auth in same popup tab; we only need app to complete login.
					}
				}

				page.bringToFront();
				waitForUiLoad(page, timeoutMs);
				assertTrue("Main app interface was not loaded after login.",
						isAnyTextVisible(page, "Negocio", "Mi Negocio", "Dashboard", "Inicio"));
				assertTrue("Left sidebar navigation was not visible after login.", isSidebarVisible(page));
				captureScreenshot(page, evidenceDir, "01-dashboard-loaded", false);
			});

			final boolean menuOk = runStep("Mi Negocio menu", report, failures, () -> {
				expandMiNegocioMenu(page, timeoutMs);
				assertTextVisible(page, "Agregar Negocio");
				assertTextVisible(page, "Administrar Negocios");
				captureScreenshot(page, evidenceDir, "02-mi-negocio-menu-expanded", false);
			}, loginOk);

			final boolean modalOk = runStep("Agregar Negocio modal", report, failures, () -> {
				clickByVisibleText(page, timeoutMs, "Agregar Negocio");
				assertTextVisible(page, "Crear Nuevo Negocio");
				assertTextVisible(page, "Nombre del Negocio");
				assertTextVisible(page, "Tienes 2 de 3 negocios");
				assertButtonVisible(page, "Cancelar");
				assertButtonVisible(page, "Crear Negocio");

				final Locator nombreInput = firstVisible(page.getByLabel("Nombre del Negocio"),
						page.locator("input[name*='nombre' i], input[placeholder*='Nombre' i]"));
				assertNotNull("The 'Nombre del Negocio' input was not found.", nombreInput);
				nombreInput.click();
				nombreInput.fill("Negocio Prueba Automatización");
				captureScreenshot(page, evidenceDir, "03-agregar-negocio-modal", false);
				clickByVisibleText(page, timeoutMs, "Cancelar");
				assertFalseVisibleText(page, "Crear Nuevo Negocio");
			}, menuOk);

			final boolean administrarOk = runStep("Administrar Negocios view", report, failures, () -> {
				expandMiNegocioMenu(page, timeoutMs);
				clickByVisibleText(page, timeoutMs, "Administrar Negocios");
				waitForUiLoad(page, timeoutMs);
				assertTextVisible(page, "Información General");
				assertTextVisible(page, "Detalles de la Cuenta");
				assertTextVisible(page, "Tus Negocios");
				assertTrue("Expected legal section was not visible.",
						isAnyTextVisible(page, "Sección Legal", "Términos y Condiciones", "Política de Privacidad"));
				captureScreenshot(page, evidenceDir, "04-administrar-negocios-full-page", true);
			}, modalOk);

			final boolean infoGeneralOk = runStep("Información General", report, failures, () -> {
				assertTextVisible(page, "Información General");
				assertTrue("User name was not visible in Información General.",
						isAnyTextVisible(page, "Nombre", "Usuario", "Name"));
				assertTrue("User email was not visible in Información General.",
						hasVisible(page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/")));
				assertTextVisible(page, "BUSINESS PLAN");
				assertButtonVisible(page, "Cambiar Plan");
			}, administrarOk);

			final boolean detallesCuentaOk = runStep("Detalles de la Cuenta", report, failures, () -> {
				assertTextVisible(page, "Detalles de la Cuenta");
				assertTextVisible(page, "Cuenta creada");
				assertTrue("Expected account active status text was not visible.",
						isAnyTextVisible(page, "Estado activo", "Estado Activo", "Activo"));
				assertTextVisible(page, "Idioma seleccionado");
			}, infoGeneralOk);

			final boolean tusNegociosOk = runStep("Tus Negocios", report, failures, () -> {
				assertTextVisible(page, "Tus Negocios");
				assertButtonVisible(page, "Agregar Negocio");
				assertTextVisible(page, "Tienes 2 de 3 negocios");

				final Locator sectionContainer = page
						.locator("xpath=//*[self::section or self::div][.//*[contains(normalize-space(.),'Tus Negocios')]]")
						.first();
				assertTrue("Business list was not visible.", hasVisible(sectionContainer.locator("li, [role='row'], article, [class*='business' i]"))
						|| hasVisible(sectionContainer.locator("button:has-text('Administrar'), button:has-text('Editar')")));
			}, detallesCuentaOk);

			final boolean termsOk = runStep("Términos y Condiciones", report, failures, () -> {
				final String url = openLegalDocument(page, context, evidenceDir, timeoutMs, "Términos y Condiciones",
						"Términos y Condiciones", "05-terminos-y-condiciones");
				legalUrls.put("Términos y Condiciones", url);
			}, tusNegociosOk);

			runStep("Política de Privacidad", report, failures, () -> {
				final String url = openLegalDocument(page, context, evidenceDir, timeoutMs, "Política de Privacidad",
						"Política de Privacidad", "06-politica-de-privacidad");
				legalUrls.put("Política de Privacidad", url);
			}, termsOk);
		}

		final Path reportFile = evidenceDir.resolve("final-report.txt");
		writeReport(reportFile, report, failures, legalUrls);

		if (!failures.isEmpty()) {
			Assert.fail("One or more SaleADS Mi Negocio validations failed. See " + reportFile + System.lineSeparator()
					+ String.join(System.lineSeparator(), failures));
		}
	}

	private void expandMiNegocioMenu(final Page page, final int timeoutMs) {
		if (!isAnyTextVisible(page, "Agregar Negocio", "Administrar Negocios")) {
			if (isAnyTextVisible(page, "Negocio")) {
				clickByVisibleText(page, timeoutMs, "Negocio");
			}
			clickByVisibleText(page, timeoutMs, "Mi Negocio");
		}
		waitForUiLoad(page, timeoutMs);
	}

	private String openLegalDocument(final Page appPage, final BrowserContext context, final Path evidenceDir, final int timeoutMs,
			final String linkText, final String expectedHeading, final String screenshotName) {
		appPage.bringToFront();
		waitForUiLoad(appPage, timeoutMs);
		final String appUrlBeforeClick = appPage.url();

		final Locator linkLocator = firstVisible(findCandidatesByText(appPage, linkText));
		assertNotNull("Could not find legal link: " + linkText, linkLocator);

		Page legalPage = appPage;
		try {
			legalPage = context.waitForPage(() -> linkLocator.click(new Locator.ClickOptions().setTimeout((double) timeoutMs)),
					new BrowserContext.WaitForPageOptions().setTimeout(8000));
		} catch (final PlaywrightException ex) {
			if (!isTimeout(ex)) {
				throw ex;
			}
		}

		waitForUiLoad(legalPage, timeoutMs);
		assertTextVisible(legalPage, expectedHeading);
		assertTrue("Legal content text was not visible for " + expectedHeading, hasVisible(legalPage.locator("p, li, article, main")));
		captureScreenshot(legalPage, evidenceDir, screenshotName, true);

		final String finalUrl = legalPage.url();
		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage, timeoutMs);
		} else if (!appUrlBeforeClick.equals(finalUrl)) {
			appPage.goBack(new Page.GoBackOptions().setTimeout((double) timeoutMs));
			waitForUiLoad(appPage, timeoutMs);
		}

		return finalUrl;
	}

	private Page clickForOptionalPopup(final Page page, final BrowserContext context, final int timeoutMs, final String... candidates) {
		final Locator locator = firstVisible(findCandidatesByText(page, candidates));
		assertNotNull("Could not find a Google login button on the login page.", locator);

		try {
			return context.waitForPage(() -> locator.click(new Locator.ClickOptions().setTimeout((double) timeoutMs)),
					new BrowserContext.WaitForPageOptions().setTimeout(10000));
		} catch (final PlaywrightException ex) {
			if (!isTimeout(ex)) {
				throw ex;
			}
			return null;
		}
	}

	private void selectGoogleAccountIfShown(final Page page, final String accountEmail, final int timeoutMs) {
		waitForUiLoad(page, timeoutMs);
		final Locator account = firstVisible(findCandidatesByText(page, accountEmail, "Usar otra cuenta", "Choose an account"));
		if (account != null && hasVisible(account)) {
			if (hasVisible(page.getByText(accountEmail, new Page.GetByTextOptions().setExact(true)))) {
				page.getByText(accountEmail, new Page.GetByTextOptions().setExact(true)).first()
						.click(new Locator.ClickOptions().setTimeout((double) timeoutMs));
			}
			waitForUiLoad(page, timeoutMs);
		}
	}

	private void clickByVisibleText(final Page page, final int timeoutMs, final String... candidates) {
		final Locator locator = firstVisible(findCandidatesByText(page, candidates));
		assertNotNull("Could not find visible element with text: " + String.join(", ", candidates), locator);
		locator.click(new Locator.ClickOptions().setTimeout((double) timeoutMs));
		waitForUiLoad(page, timeoutMs);
	}

	private List<Locator> findCandidatesByText(final Page page, final String... candidates) {
		final List<Locator> locators = new ArrayList<>();
		for (final String candidate : candidates) {
			locators.add(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(candidate)));
			locators.add(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(candidate)));
			locators.add(page.getByText(candidate, new Page.GetByTextOptions().setExact(true)));
			locators.add(page.getByText(candidate));
		}
		return locators;
	}

	private void assertButtonVisible(final Page page, final String buttonName) {
		assertTrue("Button '" + buttonName + "' was not visible.",
				hasVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(buttonName)))
						|| hasVisible(page.getByText(buttonName, new Page.GetByTextOptions().setExact(true))));
	}

	private void assertTextVisible(final Page page, final String text) {
		assertTrue("Text '" + text + "' was not visible.", isAnyTextVisible(page, text));
	}

	private void assertFalseVisibleText(final Page page, final String text) {
		final Locator textLocator = page.getByText(text, new Page.GetByTextOptions().setExact(true));
		assertTrue("Text '" + text + "' should not be visible.", !hasVisible(textLocator));
	}

	private boolean isAnyTextVisible(final Page page, final String... texts) {
		for (final String text : texts) {
			if (hasVisible(page.getByText(text, new Page.GetByTextOptions().setExact(true))) || hasVisible(page.getByText(text))) {
				return true;
			}
		}
		return false;
	}

	private boolean isSidebarVisible(final Page page) {
		return hasVisible(page.getByRole(AriaRole.NAVIGATION)) || hasVisible(page.locator("aside"))
				|| hasVisible(page.locator("[class*='sidebar' i], [id*='sidebar' i]"));
	}

	private void waitForUiLoad(final Page page, final int timeoutMs) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout((double) timeoutMs));
		} catch (final PlaywrightException ignored) {
			// Some SPAs keep background requests alive. DOM content loaded + short settle is enough.
		}
		page.waitForTimeout(500);
	}

	private void captureScreenshot(final Page page, final Path evidenceDir, final String name, final boolean fullPage) {
		final Path screenshotPath = evidenceDir.resolve(sanitize(name) + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	private Path createEvidenceDir() throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		final Path path = Paths.get("target", "saleads-evidence", TEST_NAME + "-" + timestamp);
		Files.createDirectories(path);
		return path;
	}

	private boolean runStep(final String stepName, final Map<String, String> report, final List<String> failures,
			final ThrowingRunnable action) {
		return runStep(stepName, report, failures, action, true);
	}

	private boolean runStep(final String stepName, final Map<String, String> report, final List<String> failures,
			final ThrowingRunnable action, final boolean canRun) {
		if (!canRun) {
			report.put(stepName, "FAIL");
			failures.add(stepName + ": skipped because a prerequisite step failed.");
			return false;
		}
		try {
			action.run();
			report.put(stepName, "PASS");
			return true;
		} catch (final Throwable throwable) {
			report.put(stepName, "FAIL");
			failures.add(stepName + ": " + normalizeThrowableMessage(throwable));
			return false;
		}
	}

	private void writeReport(final Path reportFile, final Map<String, String> report, final List<String> failures,
			final Map<String, String> legalUrls) throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("Test: ").append(TEST_NAME).append(System.lineSeparator());
		builder.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator()).append(System.lineSeparator());
		builder.append("Validation summary").append(System.lineSeparator());

		for (final Map.Entry<String, String> entry : report.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
		}

		if (!legalUrls.isEmpty()) {
			builder.append(System.lineSeparator()).append("Captured legal URLs").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		if (!failures.isEmpty()) {
			builder.append(System.lineSeparator()).append("Failure details").append(System.lineSeparator());
			for (final String failure : failures) {
				builder.append("- ").append(failure).append(System.lineSeparator());
			}
		}

		Files.writeString(reportFile, builder.toString(), StandardCharsets.UTF_8);
	}

	private String normalizeThrowableMessage(final Throwable throwable) {
		if (throwable == null || throwable.getMessage() == null) {
			return "Unknown error";
		}
		return throwable.getMessage().replace('\n', ' ').replace('\r', ' ').trim();
	}

	private boolean hasVisible(final Locator locator) {
		try {
			final int count = Math.min(locator.count(), 8);
			for (int i = 0; i < count; i++) {
				if (locator.nth(i).isVisible()) {
					return true;
				}
			}
		} catch (final PlaywrightException ignored) {
			// Hidden, detached or stale elements should be treated as not visible.
		}
		return false;
	}

	private Locator firstVisible(final Locator... locators) {
		final List<Locator> asList = new ArrayList<>();
		for (final Locator locator : locators) {
			asList.add(locator);
		}
		return firstVisible(asList);
	}

	private Locator firstVisible(final List<Locator> locators) {
		for (final Locator locator : locators) {
			try {
				final int count = Math.min(locator.count(), 8);
				for (int i = 0; i < count; i++) {
					final Locator candidate = locator.nth(i);
					if (candidate.isVisible()) {
						return candidate;
					}
				}
			} catch (final PlaywrightException ignored) {
				// Ignore invalid locator state and continue with other candidates.
			}
		}
		return null;
	}

	private boolean isTimeout(final PlaywrightException exception) {
		if (exception == null || exception.getMessage() == null) {
			return false;
		}
		return exception.getMessage().toLowerCase(Locale.ROOT).contains("timeout");
	}

	private int parseIntOrDefault(final String value, final int fallback) {
		if (value == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (final NumberFormatException ignored) {
			return fallback;
		}
	}

	private String sanitize(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String getSettingOrDefault(final String systemKey, final String envKey, final String fallback) {
		final String setting = getSetting(systemKey, envKey);
		return setting == null || setting.isBlank() ? fallback : setting;
	}

	private String getSetting(final String systemKey, final String envKey) {
		final String propertyValue = System.getProperty(systemKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}
		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}
		return null;
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
