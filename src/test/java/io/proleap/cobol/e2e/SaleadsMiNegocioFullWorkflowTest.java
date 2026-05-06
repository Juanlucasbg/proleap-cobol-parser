package io.proleap.cobol.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final long DEFAULT_TIMEOUT_MS = 15000;
	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final DateTimeFormatter RUN_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final boolean enabled = getBooleanProperty("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false);
		Assume.assumeTrue("Enable this test with -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true.", enabled);

		final String loginUrl = requireProperty("saleads.loginUrl", "SALEADS_LOGIN_URL");
		final boolean headless = getBooleanProperty("saleads.headless", "SALEADS_HEADLESS", true);
		final Path evidenceDir = createEvidenceDirectory();
		final Map<String, Boolean> report = initializeReport();
		final Map<String, String> urls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create();
			 Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless))) {

			final Page appPage = browser.newContext().newPage();
			appPage.navigate(loginUrl);
			waitForUi(appPage);

			// Step 1: Login with Google
			performGoogleLogin(appPage);
			validateMainInterface(appPage);
			takeScreenshot(appPage, evidenceDir, "01-dashboard-loaded.png", false);
			report.put("Login", true);

			// Step 2: Open Mi Negocio menu
			expandMiNegocioMenu(appPage);
			assertVisible(appPage, "Agregar Negocio entry",
					appPage.getByText(Pattern.compile("(?i)agregar\\s+negocio")),
					appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
							.setName(Pattern.compile("(?i)agregar\\s+negocio"))));
			assertVisible(appPage, "Administrar Negocios entry",
					appPage.getByText(Pattern.compile("(?i)administrar\\s+negocios")),
					appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
							.setName(Pattern.compile("(?i)administrar\\s+negocios"))));
			takeScreenshot(appPage, evidenceDir, "02-mi-negocio-menu-expanded.png", false);
			report.put("Mi Negocio menu", true);

			// Step 3: Validate Agregar Negocio modal
			openAgregarNegocioModal(appPage);
			assertVisible(appPage, "Crear Nuevo Negocio title",
					appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
							.setName(Pattern.compile("(?i)crear\\s+nuevo\\s+negocio"))),
					appPage.getByText(Pattern.compile("(?i)crear\\s+nuevo\\s+negocio")));
			assertVisible(appPage, "Nombre del Negocio field",
					appPage.getByLabel(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
					appPage.getByPlaceholder(Pattern.compile("(?i)nombre\\s+del\\s+negocio")));
			assertVisible(appPage, "Business quota text",
					appPage.getByText(Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios")));
			assertVisible(appPage, "Cancelar button",
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
							.setName(Pattern.compile("(?i)cancelar"))));
			assertVisible(appPage, "Crear Negocio button",
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
							.setName(Pattern.compile("(?i)crear\\s+negocio"))));
			takeScreenshot(appPage, evidenceDir, "03-agregar-negocio-modal.png", false);
			fillAndCancelAgregarNegocioModal(appPage);
			report.put("Agregar Negocio modal", true);

			// Step 4: Open Administrar Negocios
			openAdministrarNegocios(appPage);
			assertVisible(appPage, "Informacion General section",
					appPage.getByText(Pattern.compile("(?i)informaci[oó]n\\s+general")),
					appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
							.setName(Pattern.compile("(?i)informaci[oó]n\\s+general"))));
			assertVisible(appPage, "Detalles de la Cuenta section",
					appPage.getByText(Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta")),
					appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
							.setName(Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta"))));
			assertVisible(appPage, "Tus Negocios section",
					appPage.getByText(Pattern.compile("(?i)tus\\s+negocios")),
					appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
							.setName(Pattern.compile("(?i)tus\\s+negocios"))));
			assertVisible(appPage, "Seccion Legal section",
					appPage.getByText(Pattern.compile("(?i)secci[oó]n\\s+legal")),
					appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
							.setName(Pattern.compile("(?i)secci[oó]n\\s+legal"))));
			takeScreenshot(appPage, evidenceDir, "04-administrar-negocios-view.png", true);
			report.put("Administrar Negocios view", true);

			// Step 5: Validate Informacion General
			assertVisible(appPage, "User name",
					appPage.getByText(Pattern.compile("(?i)nombre")),
					appPage.getByText(Pattern.compile("(?i)usuario")));
			assertVisible(appPage, "User email",
					appPage.getByText(EMAIL_PATTERN));
			assertVisible(appPage, "BUSINESS PLAN text",
					appPage.getByText(Pattern.compile("(?i)business\\s+plan")));
			assertVisible(appPage, "Cambiar Plan button",
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
							.setName(Pattern.compile("(?i)cambiar\\s+plan"))));
			report.put("Informacion General", true);

			// Step 6: Validate Detalles de la Cuenta
			assertVisible(appPage, "Cuenta creada field",
					appPage.getByText(Pattern.compile("(?i)cuenta\\s+creada")));
			assertVisible(appPage, "Estado activo field",
					appPage.getByText(Pattern.compile("(?i)estado\\s+activo")));
			assertVisible(appPage, "Idioma seleccionado field",
					appPage.getByText(Pattern.compile("(?i)idioma\\s+seleccionado")));
			report.put("Detalles de la Cuenta", true);

			// Step 7: Validate Tus Negocios
			assertVisible(appPage, "Business list",
					appPage.getByText(Pattern.compile("(?i)tus\\s+negocios")),
					appPage.locator("table"),
					appPage.locator("ul"));
			assertVisible(appPage, "Agregar Negocio button in businesses section",
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
							.setName(Pattern.compile("(?i)agregar\\s+negocio"))));
			assertVisible(appPage, "Business quota text in businesses section",
					appPage.getByText(Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios")));
			report.put("Tus Negocios", true);

			// Step 8: Validate Terminos y Condiciones
			final String termsUrl = validateLegalPageAndReturn(appPage, evidenceDir,
					Pattern.compile("(?i)t[eé]rminos\\s+y\\s+condiciones"),
					Pattern.compile("(?i)t[eé]rminos\\s+y\\s+condiciones"),
					"08-terminos-y-condiciones.png");
			urls.put("Terminos y Condiciones URL", termsUrl);
			report.put("Terminos y Condiciones", true);

			// Step 9: Validate Politica de Privacidad
			final String privacyUrl = validateLegalPageAndReturn(appPage, evidenceDir,
					Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad"),
					Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad"),
					"09-politica-de-privacidad.png");
			urls.put("Politica de Privacidad URL", privacyUrl);
			report.put("Politica de Privacidad", true);
		} catch (Throwable t) {
			try {
				writeReport(evidenceDir, report, urls, t.getMessage());
			} catch (IOException ioException) {
				System.err.println("Failed to write failure report: " + ioException.getMessage());
			}
			printReport(report, urls);
			rethrowUnchecked(t);
			return;
		}

		writeReport(evidenceDir, report, urls, "Workflow completed successfully.");
		printReport(report, urls);
		Assert.assertTrue("At least one SaleADS Mi Negocio validation failed.", allPassed(report));
	}

	private void performGoogleLogin(final Page appPage) {
		final Locator loginButton = findVisible(appPage, "Google login button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
						.setName(Pattern.compile("(?i)(sign\\s*in|login|iniciar\\s*sesi[oó]n|continuar)\\s*(con|with)?\\s*google"))),
				appPage.getByText(Pattern.compile("(?i)(sign\\s*in|login|iniciar\\s*sesi[oó]n|continuar)\\s*(con|with)?\\s*google")),
				appPage.getByText(Pattern.compile("(?i)google")));

		clickAndWait(appPage, loginButton);

		final long end = System.currentTimeMillis() + 12000;
		while (System.currentTimeMillis() < end) {
			Page googlePage = null;
			for (Page pageCandidate : appPage.context().pages()) {
				final String currentUrl = pageCandidate.url() == null ? "" : pageCandidate.url();
				if (currentUrl.contains("accounts.google.com")) {
					googlePage = pageCandidate;
					break;
				}
			}

			if (googlePage != null) {
				final Locator accountOption = findVisible(googlePage, "Google account selector option",
						googlePage.getByText(ACCOUNT_EMAIL),
						googlePage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(ACCOUNT_EMAIL)));
				clickAndWait(googlePage, accountOption);
				waitForUi(appPage);
				return;
			}

			if (isVisible(appPage.getByRole(AriaRole.NAVIGATION)) || isVisible(appPage.locator("aside"))) {
				return;
			}
			appPage.waitForTimeout(250);
		}
	}

	private void validateMainInterface(final Page appPage) {
		assertVisible(appPage, "Main application interface",
				appPage.locator("main"),
				appPage.getByRole(AriaRole.MAIN));
		assertVisible(appPage, "Left sidebar navigation",
				appPage.locator("aside"),
				appPage.getByRole(AriaRole.NAVIGATION));
	}

	private void expandMiNegocioMenu(final Page appPage) {
		final Locator menuEntry = findVisible(appPage, "Mi Negocio menu entry",
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
						.setName(Pattern.compile("(?i)mi\\s+negocio"))),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
						.setName(Pattern.compile("(?i)mi\\s+negocio"))),
				appPage.getByText(Pattern.compile("(?i)mi\\s+negocio")),
				appPage.getByText(Pattern.compile("(?i)negocio")));
		clickAndWait(appPage, menuEntry);
	}

	private void openAgregarNegocioModal(final Page appPage) {
		final Locator addBusinessEntry = findVisible(appPage, "Agregar Negocio entry",
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar\\s+negocio"))),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar\\s+negocio"))),
				appPage.getByText(Pattern.compile("(?i)agregar\\s+negocio")));
		clickAndWait(appPage, addBusinessEntry);
		assertVisible(appPage, "Crear Nuevo Negocio modal",
				appPage.getByRole(AriaRole.DIALOG),
				appPage.getByText(Pattern.compile("(?i)crear\\s+nuevo\\s+negocio")));
	}

	private void fillAndCancelAgregarNegocioModal(final Page appPage) {
		final Locator businessNameField = findVisible(appPage, "Nombre del Negocio input",
				appPage.getByLabel(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
				appPage.getByPlaceholder(Pattern.compile("(?i)nombre\\s+del\\s+negocio")));
		businessNameField.click();
		businessNameField.fill("Negocio Prueba Automatizacion");

		final Locator cancelButton = findVisible(appPage, "Cancelar button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar"))),
				appPage.getByText(Pattern.compile("(?i)cancelar")));
		clickAndWait(appPage, cancelButton);
	}

	private void openAdministrarNegocios(final Page appPage) {
		if (!isVisible(appPage.getByText(Pattern.compile("(?i)administrar\\s+negocios")))) {
			expandMiNegocioMenu(appPage);
		}

		final Locator manageBusinessEntry = findVisible(appPage, "Administrar Negocios entry",
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)administrar\\s+negocios"))),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)administrar\\s+negocios"))),
				appPage.getByText(Pattern.compile("(?i)administrar\\s+negocios")));
		clickAndWait(appPage, manageBusinessEntry);
		waitForUi(appPage);
	}

	private String validateLegalPageAndReturn(final Page appPage, final Path evidenceDir, final Pattern linkLabel,
			final Pattern headingText, final String screenshotName) {
		final String appUrlBefore = appPage.url();
		final Locator legalLink = findVisible(appPage, "Legal link " + linkLabel,
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkLabel)),
				appPage.getByText(linkLabel));

		Page legalPage = null;
		try {
			legalPage = appPage.waitForPopup(() -> legalLink.click(), new Page.WaitForPopupOptions().setTimeout(5000));
			waitForUi(legalPage);
		} catch (TimeoutError timeoutError) {
			clickAndWait(appPage, legalLink);
			waitForUi(appPage);
			legalPage = appPage;
		}

		assertVisible(legalPage, "Legal heading", legalPage.getByRole(AriaRole.HEADING,
						new Page.GetByRoleOptions().setName(headingText)),
				legalPage.getByText(headingText));
		assertVisible(legalPage, "Legal content text",
				legalPage.locator("p"),
				legalPage.locator("article"),
				legalPage.locator("main"));
		takeScreenshot(legalPage, evidenceDir, screenshotName, true);
		final String finalUrl = legalPage.url();

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			if (!appPage.url().equals(appUrlBefore)) {
				appPage.goBack();
				waitForUi(appPage);
			}
		}
		return finalUrl;
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (PlaywrightException ignored) {
			// Some transitions in SPAs do not emit full navigation events.
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (PlaywrightException ignored) {
			// Network idle may not always be reachable due to long-lived requests.
		}

		page.waitForTimeout(500);
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.first().click(new Locator.ClickOptions().setTimeout((double) DEFAULT_TIMEOUT_MS));
		waitForUi(page);
	}

	private void assertVisible(final Page page, final String description, final Locator... candidates) {
		findVisible(page, description, candidates);
	}

	private Locator findVisible(final Page page, final String description, final Locator... candidates) {
		final long end = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS;
		while (System.currentTimeMillis() < end) {
			for (Locator locator : candidates) {
				if (isVisible(locator)) {
					return locator.first();
				}
			}
			page.waitForTimeout(250);
		}
		throw new AssertionError("Could not find visible element: " + description);
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator.count() > 0 && locator.first().isVisible();
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private Path createEvidenceDirectory() throws IOException {
		final String runId = LocalDateTime.now().format(RUN_ID_FORMATTER);
		final Path base = Path.of("target", "saleads-evidence", runId);
		Files.createDirectories(base);
		return base;
	}

	private void takeScreenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
		final Path output = evidenceDir.resolve(fileName);
		page.screenshot(new Page.ScreenshotOptions().setPath(output).setFullPage(fullPage));
	}

	private String requireProperty(final String propertyName, final String envName) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		throw new IllegalArgumentException(
				"Missing login URL. Provide -D" + propertyName + "=<url> or " + envName + "=<url>.");
	}

	private boolean getBooleanProperty(final String propertyName, final String envName, final boolean defaultValue) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null) {
			return Boolean.parseBoolean(propertyValue);
		}

		final String envValue = System.getenv(envName);
		if (envValue != null) {
			return Boolean.parseBoolean(envValue);
		}

		return defaultValue;
	}

	private Map<String, Boolean> initializeReport() {
		final Map<String, Boolean> report = new LinkedHashMap<>();
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Informacion General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Terminos y Condiciones", false);
		report.put("Politica de Privacidad", false);
		return report;
	}

	private boolean allPassed(final Map<String, Boolean> report) {
		for (boolean result : report.values()) {
			if (!result) {
				return false;
			}
		}
		return true;
	}

	private void printReport(final Map<String, Boolean> report, final Map<String, String> urls) {
		System.out.println("=== SaleADS Mi Negocio Final Report ===");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		for (Map.Entry<String, String> urlEntry : urls.entrySet()) {
			System.out.println(urlEntry.getKey() + ": " + urlEntry.getValue());
		}
	}

	private void writeReport(final Path evidenceDir, final Map<String, Boolean> report, final Map<String, String> urls,
			final String outcome) throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("Outcome: ").append(outcome).append(System.lineSeparator());
		builder.append(System.lineSeparator()).append("Validation Report").append(System.lineSeparator());
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ")
					.append(entry.getValue() ? "PASS" : "FAIL").append(System.lineSeparator());
		}
		if (!urls.isEmpty()) {
			builder.append(System.lineSeparator()).append("Captured URLs").append(System.lineSeparator());
			for (Map.Entry<String, String> urlEntry : urls.entrySet()) {
				builder.append("- ").append(urlEntry.getKey()).append(": ")
						.append(urlEntry.getValue()).append(System.lineSeparator());
			}
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), builder.toString());
	}

	private void rethrowUnchecked(final Throwable throwable) {
		if (throwable instanceof RuntimeException) {
			throw (RuntimeException) throwable;
		}
		if (throwable instanceof AssertionError) {
			throw (AssertionError) throwable;
		}
		throw new RuntimeException(throwable);
	}
}
