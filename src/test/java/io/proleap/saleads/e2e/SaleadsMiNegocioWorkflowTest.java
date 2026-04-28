package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Test;
import org.junit.Assume;

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

public class SaleadsMiNegocioWorkflowTest {

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final List<String> REPORT_FIELDS = List.of("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String loginUrl = readEnv("SALEADS_LOGIN_URL", "");
		Assume.assumeTrue(
				"Skipping SaleADS E2E test because SALEADS_LOGIN_URL is not configured for this environment.",
				!loginUrl.isBlank());
		final String expectedGoogleAccount = readEnv("SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT);
		final String expectedUserName = readEnv("SALEADS_EXPECTED_USER_NAME", "");
		final int timeoutMs = Integer.parseInt(readEnv("SALEADS_TIMEOUT_MS", "30000"));
		final boolean headless = Boolean.parseBoolean(readEnv("SALEADS_HEADLESS", "true"));

		final Path evidenceDir = createEvidenceDirectory();
		final Map<String, String> report = initializeReport();
		final Map<String, String> legalUrls = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();

		try (Playwright playwright = Playwright.create()) {
			final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(headless);

			try (Browser browser = playwright.chromium().launch(launchOptions);
					BrowserContext context = browser.newContext();
					Page appPage = context.newPage()) {

				appPage.setDefaultTimeout(timeoutMs);
				appPage.navigate(loginUrl);
				waitForUiLoad(appPage);

				executeStep(report, failures, "Login", () -> {
					final Locator loginButton = findVisible(appPage, timeoutMs, "Google login button",
							appPage.getByRole(AriaRole.BUTTON,
									new Page.GetByRoleOptions().setName("Sign in with Google")),
							appPage.getByRole(AriaRole.BUTTON,
									new Page.GetByRoleOptions().setName("Iniciar sesión con Google")),
							appPage.getByRole(AriaRole.BUTTON,
									new Page.GetByRoleOptions().setName("Continuar con Google")),
							appPage.getByText("Sign in with Google", new Page.GetByTextOptions().setExact(false)),
							appPage.getByText("Google", new Page.GetByTextOptions().setExact(false)));

					final Page authPage = clickWithPossiblePopup(context, appPage, loginButton, timeoutMs);
					selectGoogleAccountIfPresent(authPage, expectedGoogleAccount, timeoutMs);

					waitForUiLoad(appPage);
					findVisible(appPage, timeoutMs, "main application interface",
							appPage.locator("aside"), appPage.getByRole(AriaRole.NAVIGATION),
							appPage.getByText("Negocio", new Page.GetByTextOptions().setExact(false)));
					findVisible(appPage, timeoutMs, "left sidebar navigation", appPage.locator("aside"),
							appPage.getByRole(AriaRole.NAVIGATION));

					takeScreenshot(appPage, evidenceDir, "01-dashboard-loaded", true);
				});

				executeStep(report, failures, "Mi Negocio menu", () -> {
					final Locator negocioSection = findVisible(appPage, timeoutMs, "Negocio section",
							appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Negocio")),
							appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Negocio")),
							appPage.getByText("Negocio", new Page.GetByTextOptions().setExact(false)));
					clickAndWait(appPage, negocioSection);

					final Locator miNegocio = findVisible(appPage, timeoutMs, "Mi Negocio menu option",
							appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Mi Negocio")),
							appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Mi Negocio")),
							appPage.getByText("Mi Negocio", new Page.GetByTextOptions().setExact(false)));
					clickAndWait(appPage, miNegocio);

					findVisible(appPage, timeoutMs, "Agregar Negocio option",
							appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Agregar Negocio")),
							appPage.getByRole(AriaRole.BUTTON,
									new Page.GetByRoleOptions().setName("Agregar Negocio")),
							appPage.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(false)));
					findVisible(appPage, timeoutMs, "Administrar Negocios option",
							appPage.getByRole(AriaRole.LINK,
									new Page.GetByRoleOptions().setName("Administrar Negocios")),
							appPage.getByRole(AriaRole.BUTTON,
									new Page.GetByRoleOptions().setName("Administrar Negocios")),
							appPage.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(false)));

					takeScreenshot(appPage, evidenceDir, "02-mi-negocio-menu-expanded", true);
				});

				executeStep(report, failures, "Agregar Negocio modal", () -> {
					final Locator agregarNegocioAction = findVisible(appPage, timeoutMs, "Agregar Negocio trigger",
							appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Agregar Negocio")),
							appPage.getByRole(AriaRole.BUTTON,
									new Page.GetByRoleOptions().setName("Agregar Negocio")),
							appPage.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(false)));
					clickAndWait(appPage, agregarNegocioAction);

					findVisible(appPage, timeoutMs, "Crear Nuevo Negocio modal title",
							appPage.getByRole(AriaRole.HEADING,
									new Page.GetByRoleOptions().setName("Crear Nuevo Negocio")),
							appPage.getByText("Crear Nuevo Negocio", new Page.GetByTextOptions().setExact(false)));
					final Locator businessNameField = findVisible(appPage, timeoutMs, "Nombre del Negocio input",
							appPage.getByLabel("Nombre del Negocio", new Page.GetByLabelOptions().setExact(false)),
							appPage.getByPlaceholder("Nombre del Negocio",
									new Page.GetByPlaceholderOptions().setExact(false)));
					findVisible(appPage, timeoutMs, "Tienes 2 de 3 negocios indicator",
							appPage.getByText("Tienes 2 de 3 negocios", new Page.GetByTextOptions().setExact(false)));
					findVisible(appPage, timeoutMs, "Cancelar button",
							appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")));
					findVisible(appPage, timeoutMs, "Crear Negocio button",
							appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio")));

					takeScreenshot(appPage, evidenceDir, "03-crear-nuevo-negocio-modal", false);

					businessNameField.fill("Negocio Prueba Automatización");
					waitForUiLoad(appPage);
					final Locator cancelButton = findVisible(appPage, timeoutMs, "Cancelar button",
							appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")));
					clickAndWait(appPage, cancelButton);
				});

				executeStep(report, failures, "Administrar Negocios view", () -> {
					if (!isAnyVisible(appPage,
							appPage.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(false)))) {
						final Locator miNegocio = findVisible(appPage, timeoutMs, "Mi Negocio menu option",
								appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Mi Negocio")),
								appPage.getByRole(AriaRole.BUTTON,
										new Page.GetByRoleOptions().setName("Mi Negocio")),
								appPage.getByText("Mi Negocio", new Page.GetByTextOptions().setExact(false)));
						clickAndWait(appPage, miNegocio);
					}

					final Locator administrarNegocios = findVisible(appPage, timeoutMs, "Administrar Negocios option",
							appPage.getByRole(AriaRole.LINK,
									new Page.GetByRoleOptions().setName("Administrar Negocios")),
							appPage.getByRole(AriaRole.BUTTON,
									new Page.GetByRoleOptions().setName("Administrar Negocios")),
							appPage.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(false)));
					clickAndWait(appPage, administrarNegocios);

					findVisible(appPage, timeoutMs, "Información General section",
							appPage.getByText("Información General", new Page.GetByTextOptions().setExact(false)));
					findVisible(appPage, timeoutMs, "Detalles de la Cuenta section",
							appPage.getByText("Detalles de la Cuenta", new Page.GetByTextOptions().setExact(false)));
					findVisible(appPage, timeoutMs, "Tus Negocios section",
							appPage.getByText("Tus Negocios", new Page.GetByTextOptions().setExact(false)));
					findVisible(appPage, timeoutMs, "Sección Legal section",
							appPage.getByText("Sección Legal", new Page.GetByTextOptions().setExact(false)));

					takeScreenshot(appPage, evidenceDir, "04-administrar-negocios-page", true);
				});

				executeStep(report, failures, "Información General", () -> {
					final Locator infoGeneralSection = findVisible(appPage, timeoutMs, "Información General section",
							appPage.locator("section,div")
									.filter(new Locator.FilterOptions().setHasText("Información General")),
							appPage.getByText("Información General", new Page.GetByTextOptions().setExact(false)));
					final String infoText = readVisibleText(infoGeneralSection, appPage, timeoutMs);

					assertTrue("Expected user email to be visible in Información General.",
							infoText.contains("@")
									|| isAnyVisible(appPage,
											appPage.getByText(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"))));
					if (!expectedUserName.isBlank()) {
						assertTrue("Expected user name to be visible in Información General.",
								infoText.contains(expectedUserName)
										|| isAnyVisible(appPage, appPage.getByText(expectedUserName,
												new Page.GetByTextOptions().setExact(false))));
					} else {
						assertTrue("Expected a likely user name in Información General.",
								Pattern.compile("(?i)\\b[a-záéíóúñ]{2,}\\s+[a-záéíóúñ]{2,}\\b").matcher(infoText)
										.find());
					}

					findVisible(appPage, timeoutMs, "BUSINESS PLAN text",
							appPage.getByText("BUSINESS PLAN", new Page.GetByTextOptions().setExact(false)));
					findVisible(appPage, timeoutMs, "Cambiar Plan button",
							appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cambiar Plan")),
							appPage.getByText("Cambiar Plan", new Page.GetByTextOptions().setExact(false)));
				});

				executeStep(report, failures, "Detalles de la Cuenta", () -> {
					findVisible(appPage, timeoutMs, "'Cuenta creada' text",
							appPage.getByText("Cuenta creada", new Page.GetByTextOptions().setExact(false)));
					findVisible(appPage, timeoutMs, "'Estado activo' text",
							appPage.getByText("Estado activo", new Page.GetByTextOptions().setExact(false)));
					findVisible(appPage, timeoutMs, "'Idioma seleccionado' text",
							appPage.getByText("Idioma seleccionado", new Page.GetByTextOptions().setExact(false)));
				});

				executeStep(report, failures, "Tus Negocios", () -> {
					findVisible(appPage, timeoutMs, "Tus Negocios section",
							appPage.getByText("Tus Negocios", new Page.GetByTextOptions().setExact(false)));
					findVisible(appPage, timeoutMs, "Agregar Negocio button",
							appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")),
							appPage.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(false)));
					findVisible(appPage, timeoutMs, "Tienes 2 de 3 negocios text",
							appPage.getByText("Tienes 2 de 3 negocios", new Page.GetByTextOptions().setExact(false)));
				});

				executeStep(report, failures, "Términos y Condiciones", () -> {
					final LegalValidationResult termsResult = validateLegalDocument(appPage, context, timeoutMs, evidenceDir,
							"Términos y Condiciones", "05-terminos-y-condiciones");
					legalUrls.put("Términos y Condiciones URL", termsResult.finalUrl);
				});

				executeStep(report, failures, "Política de Privacidad", () -> {
					final LegalValidationResult privacyResult = validateLegalDocument(appPage, context, timeoutMs, evidenceDir,
							"Política de Privacidad", "06-politica-de-privacidad");
					legalUrls.put("Política de Privacidad URL", privacyResult.finalUrl);
				});
			}
		} finally {
			final String reportText = buildFinalReport(report, legalUrls, failures);
			Files.writeString(evidenceDir.resolve("final-report.txt"), reportText, StandardCharsets.UTF_8);
			System.out.println(reportText);

			assertTrue("One or more validations failed. See target/saleads-evidence/**/final-report.txt",
					allStepsPassed(report));
		}
	}

	private LegalValidationResult validateLegalDocument(final Page appPage, final BrowserContext context, final int timeoutMs,
			final Path evidenceDir, final String linkText, final String screenshotName) {
		final String appUrlBeforeClick = appPage.url();
		final Locator legalLink = findVisible(appPage, timeoutMs, linkText + " link",
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkText)),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(linkText)),
				appPage.getByText(linkText, new Page.GetByTextOptions().setExact(false)));

		Page legalPage = null;
		try {
			legalPage = context.waitForPage(() -> legalLink.click(), new BrowserContext.WaitForPageOptions().setTimeout(5000));
		} catch (final PlaywrightException ignored) {
			legalLink.click();
		}

		final boolean openedNewTab = legalPage != null;
		final Page currentLegalPage = openedNewTab ? legalPage : appPage;

		waitForUiLoad(currentLegalPage);
		findVisible(currentLegalPage, timeoutMs, linkText + " heading",
				currentLegalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(linkText)),
				currentLegalPage.getByText(linkText, new Page.GetByTextOptions().setExact(false)));

		final Locator contentLocator = findVisible(currentLegalPage, timeoutMs, "legal content text",
				currentLegalPage.locator("main p, article p, section p, p"));
		final String legalText = readVisibleText(contentLocator, currentLegalPage, timeoutMs);
		assertTrue("Expected legal content text to be visible for " + linkText, legalText.trim().length() > 50);

		takeScreenshot(currentLegalPage, evidenceDir, screenshotName, true);
		final String finalUrl = currentLegalPage.url();

		if (openedNewTab) {
			currentLegalPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else if (!appPage.url().equals(appUrlBeforeClick)) {
			appPage.navigate(appUrlBeforeClick);
			waitForUiLoad(appPage);
		}

		return new LegalValidationResult(finalUrl);
	}

	private Page clickWithPossiblePopup(final BrowserContext context, final Page appPage, final Locator locator,
			final int timeoutMs) {
		Page popup = null;
		try {
			popup = context.waitForPage(() -> locator.click(), new BrowserContext.WaitForPageOptions().setTimeout(5000));
		} catch (final PlaywrightException ignored) {
			locator.click();
		}

		waitForUiLoad(appPage);
		if (popup != null) {
			popup.setDefaultTimeout(timeoutMs);
			waitForUiLoad(popup);
			return popup;
		}

		return appPage;
	}

	private void selectGoogleAccountIfPresent(final Page authPage, final String accountEmail, final int timeoutMs) {
		try {
			final Locator accountChoice = findVisible(authPage, timeoutMs, "Google account chooser",
					authPage.getByText(accountEmail, new Page.GetByTextOptions().setExact(false)),
					authPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(accountEmail)),
					authPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(accountEmail)));
			clickAndWait(authPage, accountChoice);
		} catch (final AssertionError ignored) {
			// If Google account chooser is not shown, the login flow may already be authenticated.
		}
	}

	private void executeStep(final Map<String, String> report, final List<String> failures, final String reportField,
			final StepAction action) {
		try {
			action.run();
			report.put(reportField, "PASS");
		} catch (final Throwable throwable) {
			report.put(reportField, "FAIL");
			failures.add(reportField + " -> " + throwable.getMessage());
		}
	}

	private Locator findVisible(final Page page, final int timeoutMs, final String description,
			final Locator... candidates) {
		final long deadline = System.currentTimeMillis() + timeoutMs;

		while (System.currentTimeMillis() < deadline) {
			for (final Locator candidate : candidates) {
				try {
					final Locator first = candidate.first();
					if (first.count() > 0 && first.isVisible()) {
						return first;
					}
				} catch (final PlaywrightException ignored) {
				}
			}

			page.waitForTimeout(200);
		}

		throw new AssertionError("Could not find visible element: " + description);
	}

	private boolean isAnyVisible(final Page page, final Locator... candidates) {
		for (final Locator candidate : candidates) {
			try {
				final Locator first = candidate.first();
				if (first.count() > 0 && first.isVisible()) {
					return true;
				}
			} catch (final PlaywrightException ignored) {
			}
		}

		return false;
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
		locator.click();
		waitForUiLoad(page);
	}

	private void waitForUiLoad(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (final PlaywrightException ignored) {
		}
		page.waitForTimeout(600);
	}

	private String readVisibleText(final Locator primaryLocator, final Page page, final int timeoutMs) {
		primaryLocator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
		final String primaryText = primaryLocator.innerText().trim();
		if (!primaryText.isBlank()) {
			return primaryText;
		}

		final Locator fallbackContainer = page.locator("main,body").first();
		return fallbackContainer.innerText().trim();
	}

	private void takeScreenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage)
			throws IOException {
		final Path target = evidenceDir.resolve(fileName + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(target).setFullPage(fullPage));
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path evidenceDir = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private Map<String, String> initializeReport() {
		final Map<String, String> report = new LinkedHashMap<>();
		for (final String field : REPORT_FIELDS) {
			report.put(field, "FAIL");
		}
		return report;
	}

	private boolean allStepsPassed(final Map<String, String> report) {
		for (final String result : report.values()) {
			if (!"PASS".equals(result)) {
				return false;
			}
		}
		return true;
	}

	private String buildFinalReport(final Map<String, String> report, final Map<String, String> legalUrls,
			final List<String> failures) {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio - Final Report").append(System.lineSeparator());
		builder.append("================================").append(System.lineSeparator());

		for (final String field : REPORT_FIELDS) {
			builder.append(field).append(": ").append(report.get(field)).append(System.lineSeparator());
		}

		if (!legalUrls.isEmpty()) {
			builder.append(System.lineSeparator()).append("Captured legal URLs:").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue())
						.append(System.lineSeparator());
			}
		}

		if (!failures.isEmpty()) {
			builder.append(System.lineSeparator()).append("Failure details:").append(System.lineSeparator());
			for (final String failure : failures) {
				builder.append("- ").append(failure).append(System.lineSeparator());
			}
		}

		return builder.toString();
	}

	private String readEnv(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private interface StepAction {
		void run() throws Exception;
	}

	private static class LegalValidationResult {
		private final String finalUrl;

		private LegalValidationResult(final String finalUrl) {
			this.finalUrl = finalUrl;
		}
	}
}
