package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Assume;
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
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final Pattern GOOGLE_LOGIN_BUTTON_PATTERN = Pattern.compile("(?i).*(google).*");
	private static final Pattern VISIBLE_CONTENT_PATTERN = Pattern.compile("\\S");
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final String FIELD_LOGIN = "Login";
	private static final String FIELD_MENU = "Mi Negocio menu";
	private static final String FIELD_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String FIELD_ADMIN_VIEW = "Administrar Negocios view";
	private static final String FIELD_INFO_GENERAL = "Información General";
	private static final String FIELD_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String FIELD_TUS_NEGOCIOS = "Tus Negocios";
	private static final String FIELD_TERMINOS = "Términos y Condiciones";
	private static final String FIELD_PRIVACIDAD = "Política de Privacidad";

	private static final int SHORT_TIMEOUT_MS = 5_000;
	private static final int DEFAULT_TIMEOUT_MS = 15_000;
	private static final int LONG_TIMEOUT_MS = 30_000;

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String saleadsUrl = firstNonBlank(System.getProperty("saleads.url"), System.getenv("SALEADS_URL"),
				System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue("Set SALEADS_URL (or -Dsaleads.url) to run SaleADS E2E workflow test.",
				saleadsUrl != null && !saleadsUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(firstNonBlank(System.getProperty("saleads.headless"),
				System.getenv("SALEADS_HEADLESS"), "true"));
		final Path runArtifactsDirectory = createArtifactsDirectory();
		final Path screenshotDirectory = Files.createDirectories(runArtifactsDirectory.resolve("screenshots"));

		final WorkflowReport report = new WorkflowReport(saleadsUrl);

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext();
			final Page appPage = context.newPage();
			appPage.navigate(saleadsUrl);
			waitForUiToLoad(appPage);

			runStep(report, FIELD_LOGIN, () -> {
				loginWithGoogle(appPage, context);
				expectVisibleSidebar(appPage);
				report.addScreenshot(FIELD_LOGIN, captureScreenshot(appPage, screenshotDirectory, "dashboard", false));
			});

			runStep(report, FIELD_MENU, () -> {
				openMiNegocioMenu(appPage);
				expectVisibleText(appPage, "Agregar Negocio", "Mi Negocio submenu option 'Agregar Negocio'");
				expectVisibleText(appPage, "Administrar Negocios", "Mi Negocio submenu option 'Administrar Negocios'");
				report.addScreenshot(FIELD_MENU, captureScreenshot(appPage, screenshotDirectory, "mi-negocio-menu", false));
			});

			runStep(report, FIELD_AGREGAR_MODAL, () -> {
				clickByVisibleText(appPage, "Agregar Negocio");
				expectVisibleText(appPage, "Crear Nuevo Negocio", "Crear Nuevo Negocio modal title");
				expectVisibleText(appPage, "Nombre del Negocio", "Nombre del Negocio input label");
				expectVisibleText(appPage, "Tienes 2 de 3 negocios", "Business limit status text");
				expectVisibleText(appPage, "Cancelar", "Cancelar button");
				expectVisibleText(appPage, "Crear Negocio", "Crear Negocio button");

				fillFieldIfVisible(appPage, "Nombre del Negocio", "Negocio Prueba Automatización");
				report.addScreenshot(FIELD_AGREGAR_MODAL,
						captureScreenshot(appPage, screenshotDirectory, "agregar-negocio-modal", false));
				clickByVisibleText(appPage, "Cancelar");
			});

			runStep(report, FIELD_ADMIN_VIEW, () -> {
				ensureMiNegocioExpanded(appPage);
				clickByVisibleText(appPage, "Administrar Negocios");
				expectVisibleText(appPage, "Información General", "Administrar Negocios section: Información General");
				expectVisibleText(appPage, "Detalles de la Cuenta", "Administrar Negocios section: Detalles de la Cuenta");
				expectVisibleText(appPage, "Tus Negocios", "Administrar Negocios section: Tus Negocios");
				expectVisibleText(appPage, "Sección Legal", "Administrar Negocios section: Sección Legal");
				report.addScreenshot(FIELD_ADMIN_VIEW,
						captureScreenshot(appPage, screenshotDirectory, "administrar-negocios", true));
			});

			runStep(report, FIELD_INFO_GENERAL, () -> {
				expectVisibleText(appPage, "BUSINESS PLAN", "Business plan label");
				expectVisibleText(appPage, "Cambiar Plan", "Cambiar Plan button");
				expectVisibleTextMatching(appPage, Pattern.compile("(?i).+@.+\\..+"), "User email");
				expectVisibleNameText(appPage);
				report.addScreenshot(FIELD_INFO_GENERAL,
						captureScreenshot(appPage, screenshotDirectory, "informacion-general", false));
			});

			runStep(report, FIELD_DETALLES_CUENTA, () -> {
				expectVisibleText(appPage, "Cuenta creada", "Cuenta creada row");
				expectVisibleText(appPage, "Estado activo", "Estado activo row");
				expectVisibleText(appPage, "Idioma seleccionado", "Idioma seleccionado row");
				report.addScreenshot(FIELD_DETALLES_CUENTA,
						captureScreenshot(appPage, screenshotDirectory, "detalles-cuenta", false));
			});

			runStep(report, FIELD_TUS_NEGOCIOS, () -> {
				expectVisibleText(appPage, "Tus Negocios", "Tus Negocios heading");
				expectVisibleText(appPage, "Agregar Negocio", "Tus Negocios add button");
				expectVisibleText(appPage, "Tienes 2 de 3 negocios", "Tus Negocios limit text");
				expectVisibleTextMatching(appPage, Pattern.compile("(?i)negocio"), "Business list content");
				report.addScreenshot(FIELD_TUS_NEGOCIOS, captureScreenshot(appPage, screenshotDirectory, "tus-negocios", false));
			});

			runStep(report, FIELD_TERMINOS, () -> validateLegalLink(appPage, context, report, screenshotDirectory,
					FIELD_TERMINOS, "Términos y Condiciones", "Términos y Condiciones"));

			runStep(report, FIELD_PRIVACIDAD, () -> validateLegalLink(appPage, context, report, screenshotDirectory,
					FIELD_PRIVACIDAD, "Política de Privacidad", "Política de Privacidad"));
		}

		final Path reportFile = runArtifactsDirectory.resolve("saleads-mi-negocio-report.json");
		Files.writeString(reportFile, report.toJson(), StandardCharsets.UTF_8);
		assertTrue("One or more SaleADS Mi Negocio validations failed.\n" + report.toConsoleSummary(), report.allPassed());
	}

	private void loginWithGoogle(final Page appPage, final BrowserContext context) {
		final Locator loginButton = waitForAnyVisible("Google login button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GOOGLE_LOGIN_BUTTON_PATTERN)),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(GOOGLE_LOGIN_BUTTON_PATTERN)),
				appPage.getByText(Pattern.compile("(?i).*(sign in|iniciar sesi[oó]n|continuar).*(google).*")));

		Page googlePopup = null;
		try {
			googlePopup = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(SHORT_TIMEOUT_MS),
					() -> loginButton.click());
		} catch (final TimeoutError timeout) {
			waitForUiToLoad(appPage);
		}

		if (googlePopup != null) {
			googlePopup.waitForLoadState(LoadState.DOMCONTENTLOADED);
			maybeClickText(googlePopup, GOOGLE_ACCOUNT_EMAIL);
			waitForUiToLoad(googlePopup);
			waitForUiToLoad(appPage);
		} else {
			maybeClickText(appPage, GOOGLE_ACCOUNT_EMAIL);
			waitForUiToLoad(appPage);
		}
	}

	private void openMiNegocioMenu(final Page appPage) {
		final Locator negocioSection = waitForAnyVisible("Sidebar Negocio section",
				appPage.getByText(Pattern.compile("(?i)^\\s*Negocio\\s*$")),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*Negocio.*"))),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*Negocio.*"))));
		negocioSection.click();
		waitForUiToLoad(appPage);
		clickByVisibleText(appPage, "Mi Negocio");
	}

	private void ensureMiNegocioExpanded(final Page appPage) {
		if (isVisible(appPage, "Administrar Negocios", SHORT_TIMEOUT_MS)) {
			return;
		}
		if (!isVisible(appPage, "Mi Negocio", SHORT_TIMEOUT_MS)) {
			openMiNegocioMenu(appPage);
		} else {
			clickByVisibleText(appPage, "Mi Negocio");
		}
	}

	private void validateLegalLink(final Page appPage, final BrowserContext context, final WorkflowReport report,
			final Path screenshotDirectory, final String fieldName, final String linkText, final String expectedHeading)
			throws IOException {
		final Locator legalLink = waitForAnyVisible("Legal link '" + linkText + "'",
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*" + Pattern.quote(linkText) + ".*"))),
				appPage.getByText(Pattern.compile("(?i).*" + Pattern.quote(linkText) + ".*")));

		Page openedPage = null;
		try {
			openedPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(SHORT_TIMEOUT_MS),
					() -> legalLink.click());
		} catch (final TimeoutError timeout) {
			waitForUiToLoad(appPage);
		}

		if (openedPage != null) {
			openedPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
			expectVisibleText(openedPage, expectedHeading, "Legal heading: " + expectedHeading);
			expectLegalBodyText(openedPage);
			report.addScreenshot(fieldName, captureScreenshot(openedPage, screenshotDirectory, normalizeFileName(fieldName), true));
			report.setUrl(fieldName, openedPage.url());
			openedPage.close();
			appPage.bringToFront();
			waitForUiToLoad(appPage);
			return;
		}

		expectVisibleText(appPage, expectedHeading, "Legal heading: " + expectedHeading);
		expectLegalBodyText(appPage);
		report.addScreenshot(fieldName, captureScreenshot(appPage, screenshotDirectory, normalizeFileName(fieldName), true));
		report.setUrl(fieldName, appPage.url());
		appPage.goBack(new Page.GoBackOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUiToLoad(appPage);
	}

	private void expectVisibleSidebar(final Page appPage) {
		final Locator sidebar = waitForAnyVisible("Left sidebar",
				appPage.locator("aside"),
				appPage.getByRole(AriaRole.NAVIGATION),
				appPage.getByText(Pattern.compile("(?i).*Negocio.*")));
		sidebar.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
	}

	private void expectVisibleNameText(final Page appPage) {
		expectVisibleTextMatching(appPage, Pattern.compile("(?i)(nombre|name|usuario|perfil)"), "User name label");
	}

	private void expectLegalBodyText(final Page page) {
		final Locator body = page.locator("body");
		body.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
		final String bodyText = body.innerText();
		if (bodyText == null || bodyText.length() < 100 || !VISIBLE_CONTENT_PATTERN.matcher(bodyText).find()) {
			throw new AssertionError("Expected legal body content to be visible.");
		}
	}

	private void expectVisibleText(final Page page, final String text, final String description) {
		final Pattern containsTextPattern = Pattern.compile("(?i).*" + Pattern.quote(text) + ".*");
		waitForAnyVisible(description, page.getByText(containsTextPattern),
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(containsTextPattern)),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(containsTextPattern)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(containsTextPattern)));
	}

	private void expectVisibleTextMatching(final Page page, final Pattern pattern, final String description) {
		waitForAnyVisible(description, page.getByText(pattern),
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(pattern)),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern)));
	}

	private void clickByVisibleText(final Page page, final String text) {
		final Pattern pattern = Pattern.compile("(?i).*" + Pattern.quote(text) + ".*");
		final Locator clickable = waitForAnyVisible("Clickable element '" + text + "'",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern)),
				page.getByText(pattern));
		clickable.click();
		waitForUiToLoad(page);
	}

	private void fillFieldIfVisible(final Page page, final String labelText, final String value) {
		final Pattern labelPattern = Pattern.compile("(?i).*" + Pattern.quote(labelText) + ".*");
		try {
			final Locator field = waitForAnyVisible("Input field '" + labelText + "'",
					page.getByLabel(labelPattern),
					page.getByPlaceholder(labelPattern),
					page.locator("input").filter(new Locator.FilterOptions().setHasText(labelPattern)));
			field.fill(value);
		} catch (final RuntimeException ignored) {
			// Optional action in the workflow.
		}
	}

	private void maybeClickText(final Page page, final String text) {
		final Pattern pattern = Pattern.compile("(?i).*" + Pattern.quote(text) + ".*");
		try {
			final Locator accountOption = waitForAnyVisible("Optional text '" + text + "'",
					page.getByText(pattern),
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern)),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern)));
			accountOption.click();
		} catch (final RuntimeException ignored) {
			// Optional action; account picker might not appear when already authenticated.
		}
	}

	private boolean isVisible(final Page page, final String text, final int timeoutMs) {
		final Pattern pattern = Pattern.compile("(?i).*" + Pattern.quote(text) + ".*");
		final Locator locator = page.getByText(pattern).first();
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (final RuntimeException ex) {
			return false;
		}
	}

	private Locator waitForAnyVisible(final String description, final Locator... locators) {
		RuntimeException lastError = null;
		for (final Locator locator : locators) {
			final Locator first = locator.first();
			try {
				first.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
				return first;
			} catch (final RuntimeException ex) {
				lastError = ex;
			}
		}

		final String message = "No visible element found for: " + description;
		if (lastError == null) {
			throw new AssertionError(message);
		}
		throw new AssertionError(message, lastError);
	}

	private String captureScreenshot(final Page page, final Path screenshotDirectory, final String baseName, final boolean fullPage)
			throws IOException {
		final String fileName = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.ROOT)
				.format(LocalDateTime.now(ZoneOffset.UTC)) + "_" + normalizeFileName(baseName) + ".png";
		final Path screenshotPath = screenshotDirectory.resolve(fileName);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
		return screenshotPath.toString();
	}

	private Path createArtifactsDirectory() throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT)
				.format(LocalDateTime.now(ZoneOffset.UTC));
		final Path runDir = Paths.get("target", "saleads-mi-negocio", timestamp);
		return Files.createDirectories(runDir);
	}

	private void waitForUiToLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (final RuntimeException ignored) {
			// Some SPAs never reach network idle. Continue with DOM loaded state.
		}
		page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(LONG_TIMEOUT_MS));
	}

	private String normalizeFileName(final String rawName) {
		return rawName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	private void runStep(final WorkflowReport report, final String fieldName, final StepAction action) {
		try {
			action.run();
			report.pass(fieldName);
		} catch (final Throwable throwable) {
			report.fail(fieldName, throwable.getMessage());
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static final class WorkflowReport {
		private final String baseUrl;
		private final String executedAt;
		private final Map<String, StepResult> resultsByField;

		private WorkflowReport(final String baseUrl) {
			this.baseUrl = baseUrl;
			this.executedAt = Instant.now().toString();
			this.resultsByField = new LinkedHashMap<>();
			initializeExpectedFields();
		}

		private void initializeExpectedFields() {
			resultsByField.put(FIELD_LOGIN, new StepResult(FIELD_LOGIN));
			resultsByField.put(FIELD_MENU, new StepResult(FIELD_MENU));
			resultsByField.put(FIELD_AGREGAR_MODAL, new StepResult(FIELD_AGREGAR_MODAL));
			resultsByField.put(FIELD_ADMIN_VIEW, new StepResult(FIELD_ADMIN_VIEW));
			resultsByField.put(FIELD_INFO_GENERAL, new StepResult(FIELD_INFO_GENERAL));
			resultsByField.put(FIELD_DETALLES_CUENTA, new StepResult(FIELD_DETALLES_CUENTA));
			resultsByField.put(FIELD_TUS_NEGOCIOS, new StepResult(FIELD_TUS_NEGOCIOS));
			resultsByField.put(FIELD_TERMINOS, new StepResult(FIELD_TERMINOS));
			resultsByField.put(FIELD_PRIVACIDAD, new StepResult(FIELD_PRIVACIDAD));
		}

		private void addScreenshot(final String field, final String screenshotPath) {
			resultsByField.get(field).screenshots.add(screenshotPath);
		}

		private void setUrl(final String field, final String url) {
			resultsByField.get(field).finalUrl = url;
		}

		private void pass(final String field) {
			final StepResult result = resultsByField.get(field);
			result.status = "PASS";
			result.reason = null;
		}

		private void fail(final String field, final String reason) {
			final StepResult result = resultsByField.get(field);
			result.status = "FAIL";
			result.reason = reason == null ? "Unknown failure" : reason;
		}

		private boolean allPassed() {
			for (final StepResult result : resultsByField.values()) {
				if (!"PASS".equals(result.status)) {
					return false;
				}
			}
			return true;
		}

		private String toConsoleSummary() {
			final StringBuilder builder = new StringBuilder();
			for (final StepResult result : resultsByField.values()) {
				builder.append("- ").append(result.field).append(": ").append(result.status);
				if (result.reason != null) {
					builder.append(" (").append(result.reason).append(")");
				}
				builder.append('\n');
			}
			return builder.toString();
		}

		private String toJson() {
			final StringBuilder builder = new StringBuilder();
			builder.append("{\n");
			builder.append("  \"name\": \"saleads_mi_negocio_full_test\",\n");
			builder.append("  \"executed_at\": \"").append(escape(executedAt)).append("\",\n");
			builder.append("  \"base_url\": \"").append(escape(baseUrl)).append("\",\n");
			builder.append("  \"overall_status\": \"").append(allPassed() ? "PASS" : "FAIL").append("\",\n");
			builder.append("  \"results\": [\n");

			int index = 0;
			for (final StepResult result : resultsByField.values()) {
				if (index > 0) {
					builder.append(",\n");
				}
				builder.append("    {\n");
				builder.append("      \"field\": \"").append(escape(result.field)).append("\",\n");
				builder.append("      \"status\": \"").append(escape(result.status)).append("\",\n");
				builder.append("      \"reason\": ");
				if (result.reason == null) {
					builder.append("null,\n");
				} else {
					builder.append("\"").append(escape(result.reason)).append("\",\n");
				}
				builder.append("      \"screenshots\": [");
				for (int i = 0; i < result.screenshots.size(); i++) {
					if (i > 0) {
						builder.append(", ");
					}
					builder.append("\"").append(escape(result.screenshots.get(i))).append("\"");
				}
				builder.append("],\n");
				builder.append("      \"final_url\": ");
				if (result.finalUrl == null) {
					builder.append("null\n");
				} else {
					builder.append("\"").append(escape(result.finalUrl)).append("\"\n");
				}
				builder.append("    }");
				index++;
			}

			builder.append("\n  ]\n");
			builder.append("}\n");
			return builder.toString();
		}

		private String escape(final String value) {
			if (value == null) {
				return "";
			}
			return value.replace("\\", "\\\\").replace("\"", "\\\"");
		}
	}

	private static final class StepResult {
		private final String field;
		private String status;
		private String reason;
		private String finalUrl;
		private final List<String> screenshots;

		private StepResult(final String field) {
			this.field = field;
			this.status = "FAIL";
			this.reason = "Step did not execute.";
			this.screenshots = new ArrayList<>();
		}
	}
}
