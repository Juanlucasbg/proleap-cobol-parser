package io.proleap.cobol.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.LoadState;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SaleadsMiNegocioWorkflowTest {

	private static final String DEFAULT_EXPECTED_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Pattern GOOGLE_SIGN_IN_PATTERN = Pattern.compile(
			"(?i)(sign in with google|continue with google|iniciar sesi[oó]n con google|continuar con google)");
	private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss",
			Locale.ROOT);

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String startUrl = readSetting("saleads.start.url", "SALEADS_START_URL", null);
		Assume.assumeTrue(
				"Set -Dsaleads.start.url or SALEADS_START_URL to the current environment login page before running this test.",
				isNotBlank(startUrl));

		final boolean headless = Boolean.parseBoolean(readSetting("saleads.headless", "SALEADS_HEADLESS", "true"));
		final String expectedUserEmail = readSetting("saleads.expected.user.email", "SALEADS_EXPECTED_USER_EMAIL",
				DEFAULT_EXPECTED_EMAIL);
		final String expectedUserName = readSetting("saleads.expected.user.name", "SALEADS_EXPECTED_USER_NAME", null);
		final int timeoutMs = Integer.parseInt(readSetting("saleads.timeout.ms", "SALEADS_TIMEOUT_MS", "45000"));
		final Path evidenceDirectory = createEvidenceDirectory();

		final Map<String, StepResult> report = initializeReport();
		final String[] legalUrls = new String[] { "N/A", "N/A" };

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser
					.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
			final Page appPage = context.newPage();
			appPage.setDefaultTimeout(timeoutMs);

			appPage.navigate(startUrl);
			waitForUi(appPage);

			final boolean loginStep = runStep(report, "Login", () -> {
				final int pagesBeforeLogin = context.pages().size();
				final Locator signInButton = findLoginButton(appPage);
				clickAndWait(appPage, signInButton);
				final Page googlePage = waitForPopupIfAny(context, pagesBeforeLogin, 7000);
				if (googlePage != null) {
					waitForUi(googlePage);
					selectGoogleAccountIfVisible(googlePage, expectedUserEmail);
					appPage.bringToFront();
				} else {
					selectGoogleAccountIfVisible(appPage, expectedUserEmail);
				}

				waitForUi(appPage);
				assertAnyVisible("Main application interface appears",
						Arrays.asList(appPage.locator("aside"), appPage.getByText("Negocio"),
								appPage.getByText("Mi Negocio")));
				assertAnyVisible("Left sidebar navigation is visible",
						Arrays.asList(appPage.locator("aside"), appPage.getByText("Negocio")));
				captureScreenshot(appPage, evidenceDirectory, "01-dashboard-loaded", true);
			});

			final boolean menuStep = runStep(report, "Mi Negocio menu", () -> {
				require(loginStep, "Login step failed.");
				expandMiNegocioMenu(appPage);
				assertTextVisible(appPage, "Agregar Negocio");
				assertTextVisible(appPage, "Administrar Negocios");
				captureScreenshot(appPage, evidenceDirectory, "02-mi-negocio-menu-expanded", false);
			});

			final boolean addBusinessModalStep = runStep(report, "Agregar Negocio modal", () -> {
				require(menuStep, "Mi Negocio menu step failed.");
				clickByVisibleText(appPage, "Agregar Negocio");
				assertTextVisible(appPage, "Crear Nuevo Negocio");
				assertAnyVisible("Input field 'Nombre del Negocio' exists", Arrays.asList(
						appPage.getByLabel("Nombre del Negocio"), appPage.getByPlaceholder("Nombre del Negocio"),
						appPage.locator("input[placeholder*='Nombre del Negocio']")));
				assertTextVisible(appPage, "Tienes 2 de 3 negocios");
				assertClickableByText(appPage, "Cancelar");
				assertClickableByText(appPage, "Crear Negocio");
				captureScreenshot(appPage, evidenceDirectory, "03-agregar-negocio-modal", false);

				final Locator businessNameInput = firstVisible(Arrays.asList(appPage.getByLabel("Nombre del Negocio"),
						appPage.getByPlaceholder("Nombre del Negocio"),
						appPage.locator("input[placeholder*='Nombre del Negocio']")));
				businessNameInput.click();
				businessNameInput.fill("Negocio Prueba Automatizacion");
				clickByVisibleText(appPage, "Cancelar");
				assertTextHidden(appPage, "Crear Nuevo Negocio");
			});

			final boolean adminViewStep = runStep(report, "Administrar Negocios view", () -> {
				require(addBusinessModalStep, "Agregar Negocio modal step failed.");
				expandMiNegocioMenu(appPage);
				clickByVisibleText(appPage, "Administrar Negocios");
				assertTextVisible(appPage, "Información General");
				assertTextVisible(appPage, "Detalles de la Cuenta");
				assertTextVisible(appPage, "Tus Negocios");
				assertTextVisible(appPage, "Sección Legal");
				captureScreenshot(appPage, evidenceDirectory, "04-administrar-negocios", true);
			});

			final boolean infoGeneralStep = runStep(report, "Información General", () -> {
				require(adminViewStep, "Administrar Negocios view step failed.");
				assertTextVisible(appPage, "Información General");

				if (isNotBlank(expectedUserName)) {
					assertTextVisible(appPage, expectedUserName);
				} else {
					final String generalInfoBlock = extractSectionBlock(appPage, "Información General",
							"Detalles de la Cuenta");
					assertUserNameHeuristic(generalInfoBlock);
				}

				if (isNotBlank(expectedUserEmail)) {
					assertTextVisible(appPage, expectedUserEmail);
				} else {
					assertEmailVisible(appPage);
				}

				assertTextVisible(appPage, "BUSINESS PLAN");
				assertClickableByText(appPage, "Cambiar Plan");
			});

			final boolean accountDetailsStep = runStep(report, "Detalles de la Cuenta", () -> {
				require(infoGeneralStep, "Información General step failed.");
				assertTextVisible(appPage, "Cuenta creada");
				assertTextVisible(appPage, "Estado activo");
				assertTextVisible(appPage, "Idioma seleccionado");
			});

			final boolean businessesStep = runStep(report, "Tus Negocios", () -> {
				require(accountDetailsStep, "Detalles de la Cuenta step failed.");
				assertTextVisible(appPage, "Tus Negocios");
				assertClickableByText(appPage, "Agregar Negocio");
				assertTextVisible(appPage, "Tienes 2 de 3 negocios");
			});

			final boolean termsStep = runStep(report, "Términos y Condiciones", () -> {
				require(businessesStep, "Tus Negocios step failed.");
				legalUrls[0] = validateLegalDocument(appPage, context, evidenceDirectory, "Términos y Condiciones",
						"08-terminos-y-condiciones");
			});

			runStep(report, "Política de Privacidad", () -> {
				require(termsStep, "Términos y Condiciones step failed.");
				legalUrls[1] = validateLegalDocument(appPage, context, evidenceDirectory, "Política de Privacidad",
						"09-politica-de-privacidad");
			});

			final Path reportPath = evidenceDirectory.resolve("10-final-report.txt");
			writeFinalReport(reportPath, report, legalUrls, evidenceDirectory);
			assertAllPassed(report, reportPath);
		}
	}

	private void assertAllPassed(final Map<String, StepResult> report, final Path reportPath) {
		final List<String> failures = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!"PASS".equals(entry.getValue().status)) {
				failures.add(entry.getKey() + " => " + entry.getValue().status + " :: " + entry.getValue().details);
			}
		}

		if (!failures.isEmpty()) {
			final String message = "One or more SaleADS validations failed. See " + reportPath + "\n"
					+ String.join("\n", failures);
			Assert.fail(message);
		}
	}

	private String validateLegalDocument(final Page appPage, final BrowserContext context, final Path evidenceDirectory,
			final String linkText, final String screenshotPrefix) throws IOException {
		final int pageCountBefore = context.pages().size();
		clickByVisibleText(appPage, linkText);

		Page legalPage = waitForPopupIfAny(context, pageCountBefore, 7000);
		if (legalPage == null) {
			legalPage = appPage;
		}

		waitForUi(legalPage);
		assertTextVisible(legalPage, linkText);
		assertLegalContentVisible(legalPage, linkText);
		captureScreenshot(legalPage, evidenceDirectory, screenshotPrefix, true);
		final String finalUrl = legalPage.url();

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			appPage.goBack();
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private void assertLegalContentVisible(final Page page, final String documentName) {
		final String bodyText = safeBodyText(page);
		if (bodyText.length() < 120) {
			throw new IllegalStateException(
					documentName + " appears to have insufficient legal content text. Body length: " + bodyText.length());
		}
	}

	private void assertUserNameHeuristic(final String generalInfoBlock) {
		final List<String> ignoredLabels = Arrays.asList("información general", "business plan", "cambiar plan", "plan",
				"detalles de la cuenta", "tus negocios", "sección legal", "cuenta creada", "estado activo",
				"idioma seleccionado");

		for (final String line : generalInfoBlock.split("\\R")) {
			final String trimmed = line.trim();
			if (trimmed.length() < 3 || EMAIL_PATTERN.matcher(trimmed).find() || trimmed.matches(".*\\d.*")) {
				continue;
			}

			final String normalized = trimmed.toLowerCase(Locale.ROOT);
			boolean ignore = false;
			for (final String label : ignoredLabels) {
				if (normalized.contains(label)) {
					ignore = true;
					break;
				}
			}

			if (!ignore && trimmed.split("\\s+").length >= 2) {
				return;
			}
		}

		throw new IllegalStateException(
				"Could not detect user name-like text in the 'Información General' section. Set SALEADS_EXPECTED_USER_NAME to validate explicitly.");
	}

	private String extractSectionBlock(final Page page, final String sectionStartText, final String sectionEndText) {
		final String body = safeBodyText(page);
		final int startIdx = indexOfIgnoreCase(body, sectionStartText);
		if (startIdx < 0) {
			return body;
		}

		int endIdx = indexOfIgnoreCase(body.substring(startIdx), sectionEndText);
		if (endIdx < 0) {
			endIdx = Math.min(body.length() - startIdx, 1400);
		}

		return body.substring(startIdx, startIdx + endIdx);
	}

	private int indexOfIgnoreCase(final String text, final String searchText) {
		return text.toLowerCase(Locale.ROOT).indexOf(searchText.toLowerCase(Locale.ROOT));
	}

	private void assertEmailVisible(final Page page) {
		final String bodyText = safeBodyText(page);
		final Matcher matcher = EMAIL_PATTERN.matcher(bodyText);
		if (!matcher.find()) {
			throw new IllegalStateException("No email-like text is visible on screen.");
		}
	}

	private void expandMiNegocioMenu(final Page page) {
		if (isTextVisible(page, "Agregar Negocio") && isTextVisible(page, "Administrar Negocios")) {
			return;
		}

		if (isTextVisible(page, "Mi Negocio")) {
			clickByVisibleText(page, "Mi Negocio");
		} else if (isTextVisible(page, "Negocio")) {
			clickByVisibleText(page, "Negocio");
			clickByVisibleText(page, "Mi Negocio");
		} else {
			throw new IllegalStateException("Could not find 'Mi Negocio' in sidebar.");
		}

		assertTextVisible(page, "Agregar Negocio");
		assertTextVisible(page, "Administrar Negocios");
	}

	private void selectGoogleAccountIfVisible(final Page page, final String expectedEmail) {
		if (!isNotBlank(expectedEmail)) {
			return;
		}

		final List<Locator> accountLocators = Arrays.asList(
				page.getByText(expectedEmail, new Page.GetByTextOptions().setExact(true)),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(expectedEmail)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(expectedEmail)));
		final Locator account = firstVisibleOrNull(accountLocators);
		if (account != null) {
			clickAndWait(page, account);
		}
	}

	private Locator findLoginButton(final Page page) {
		final List<Locator> candidates = Arrays.asList(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GOOGLE_SIGN_IN_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(GOOGLE_SIGN_IN_PATTERN)),
				page.getByText("Sign in with Google", new Page.GetByTextOptions().setExact(true)),
				page.getByText("Iniciar sesión con Google", new Page.GetByTextOptions().setExact(true)),
				page.getByText("Continuar con Google", new Page.GetByTextOptions().setExact(true)));
		final Locator loginButton = firstVisibleOrNull(candidates);
		if (loginButton == null) {
			throw new IllegalStateException("Could not find the Google login button.");
		}
		return loginButton;
	}

	private void writeFinalReport(final Path reportPath, final Map<String, StepResult> report, final String[] legalUrls,
			final Path evidenceDirectory) throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Full Test Report\n");
		builder.append("===================================\n");
		builder.append("Evidence directory: ").append(evidenceDirectory).append('\n');
		builder.append('\n');

		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().status);
			if (isNotBlank(entry.getValue().details)) {
				builder.append(" (").append(entry.getValue().details).append(')');
			}
			builder.append('\n');
		}

		builder.append('\n');
		builder.append("Términos y Condiciones URL: ").append(legalUrls[0]).append('\n');
		builder.append("Política de Privacidad URL: ").append(legalUrls[1]).append('\n');

		Files.writeString(reportPath, builder.toString());
	}

	private boolean runStep(final Map<String, StepResult> report, final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, StepResult.pass());
			return true;
		} catch (final Exception ex) {
			report.put(stepName, StepResult.fail(ex.getMessage()));
			return false;
		}
	}

	private Map<String, StepResult> initializeReport() {
		final Map<String, StepResult> report = new LinkedHashMap<>();
		report.put("Login", StepResult.notRun());
		report.put("Mi Negocio menu", StepResult.notRun());
		report.put("Agregar Negocio modal", StepResult.notRun());
		report.put("Administrar Negocios view", StepResult.notRun());
		report.put("Información General", StepResult.notRun());
		report.put("Detalles de la Cuenta", StepResult.notRun());
		report.put("Tus Negocios", StepResult.notRun());
		report.put("Términos y Condiciones", StepResult.notRun());
		report.put("Política de Privacidad", StepResult.notRun());
		return report;
	}

	private void clickByVisibleText(final Page page, final String text) {
		final Locator locator = firstVisible(Arrays.asList(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text).setExact(true)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text).setExact(true)),
				page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(text).setExact(true)),
				page.getByText(text, new Page.GetByTextOptions().setExact(true))));
		clickAndWait(page, locator);
	}

	private void assertClickableByText(final Page page, final String text) {
		final Locator locator = firstVisible(Arrays.asList(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text).setExact(true)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text).setExact(true)),
				page.getByText(text, new Page.GetByTextOptions().setExact(true))));
		if (!locator.isVisible()) {
			throw new IllegalStateException("Element '" + text + "' is not clickable or visible.");
		}
	}

	private void assertTextVisible(final Page page, final String text) {
		final Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
		locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
	}

	private void assertTextHidden(final Page page, final String text) {
		final Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
		locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
	}

	private boolean isTextVisible(final Page page, final String text) {
		try {
			return page.getByText(text, new Page.GetByTextOptions().setExact(true)).first().isVisible();
		} catch (final Exception ex) {
			return false;
		}
	}

	private void assertAnyVisible(final String assertionDescription, final List<Locator> locators) {
		if (firstVisibleOrNull(locators) == null) {
			throw new IllegalStateException(assertionDescription + " failed.");
		}
	}

	private Locator firstVisible(final List<Locator> locators) {
		final Locator result = firstVisibleOrNull(locators);
		if (result == null) {
			throw new IllegalStateException("No visible locator found among candidates.");
		}
		return result;
	}

	private Locator firstVisibleOrNull(final List<Locator> locators) {
		for (final Locator locator : locators) {
			try {
				if (locator.count() > 0 && locator.first().isVisible()) {
					return locator.first();
				}
			} catch (final Exception ex) {
				// Candidate not available in this view, continue with next one.
			}
		}
		return null;
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.first().click();
		waitForUi(page);
	}

	private Page waitForPopupIfAny(final BrowserContext context, final int pagesBeforeClick, final long timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			final List<Page> pages = context.pages();
			if (pages.size() > pagesBeforeClick) {
				return pages.get(pages.size() - 1);
			}
			try {
				Thread.sleep(150L);
			} catch (final InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				return null;
			}
		}
		return null;
	}

	private void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		page.waitForTimeout(600);
	}

	private Path createEvidenceDirectory() throws IOException {
		final String runTimestamp = TIMESTAMP_FORMATTER.format(LocalDateTime.now());
		final Path evidenceDir = Paths.get("target", "saleads-mi-negocio-evidence", runTimestamp);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private void captureScreenshot(final Page page, final Path evidenceDir, final String checkpointName,
			final boolean fullPage) {
		final String normalizedName = checkpointName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-");
		final Path screenshotPath = evidenceDir.resolve(normalizedName + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	private String safeBodyText(final Page page) {
		final String body = page.locator("body").innerText();
		return body == null ? "" : body.trim();
	}

	private void require(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}

	private String readSetting(final String propertyName, final String envName, final String defaultValue) {
		final String fromProperty = System.getProperty(propertyName);
		if (isNotBlank(fromProperty)) {
			return fromProperty.trim();
		}

		final String fromEnv = System.getenv(envName);
		if (isNotBlank(fromEnv)) {
			return fromEnv.trim();
		}

		return defaultValue;
	}

	private boolean isNotBlank(final String value) {
		return value != null && !value.trim().isEmpty();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static class StepResult {
		private final String details;
		private final String status;

		private StepResult(final String status, final String details) {
			this.status = status;
			this.details = details;
		}

		private static StepResult pass() {
			return new StepResult("PASS", "");
		}

		private static StepResult fail(final String details) {
			return new StepResult("FAIL", details == null ? "No details available." : details);
		}

		private static StepResult notRun() {
			return new StepResult("NOT RUN", "");
		}
	}
}
