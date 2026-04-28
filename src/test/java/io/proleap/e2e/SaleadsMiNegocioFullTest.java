package io.proleap.e2e;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final long DEFAULT_TIMEOUT_MS = 20000;

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		Assume.assumeTrue("Set RUN_SALEADS_E2E=true to run this external workflow test.",
				Boolean.parseBoolean(System.getenv().getOrDefault("RUN_SALEADS_E2E", "false")));

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to the login page URL for the current SaleADS environment.",
				loginUrl != null && !loginUrl.isBlank());

		final String googleEmail = System.getenv().getOrDefault("SALEADS_GOOGLE_EMAIL", DEFAULT_GOOGLE_EMAIL);
		final long timeoutMs = Long.parseLong(System.getenv().getOrDefault("SALEADS_E2E_TIMEOUT_MS",
				String.valueOf(DEFAULT_TIMEOUT_MS)));
		final boolean headless = Boolean
				.parseBoolean(System.getenv().getOrDefault("PLAYWRIGHT_HEADLESS", "true"));

		final Path evidenceDir = createEvidenceDirectory();
		final Map<String, String> statusByField = initializeStatus();
		final Map<String, String> noteByField = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser
					.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
			final Page page = context.newPage();

			page.navigate(loginUrl);
			waitForUi(page);

			final boolean loginOk = runStep("Login", statusByField, noteByField, failures, () -> {
				loginWithGoogle(page, googleEmail, timeoutMs);
				requireVisible(page, "Negocio", timeoutMs);
				requireSidebarVisible(page, timeoutMs);
				captureScreenshot(page, evidenceDir, "01_dashboard_loaded.png", true);
			});

			if (!loginOk) {
				markRemainingAsFailed(statusByField, noteByField, failures, "Login failed; dependent step not executed.");
				final Path reportPath = writeFinalReport(evidenceDir, statusByField, noteByField, legalUrls, failures);
				fail("Workflow failed at login. Report: " + reportPath.toAbsolutePath());
			}

			runStep("Mi Negocio menu", statusByField, noteByField, failures, () -> {
				clickByVisibleText(page, "Negocio", timeoutMs);
				clickByVisibleText(page, "Mi Negocio", timeoutMs);
				requireVisible(page, "Agregar Negocio", timeoutMs);
				requireVisible(page, "Administrar Negocios", timeoutMs);
				captureScreenshot(page, evidenceDir, "02_mi_negocio_menu_expanded.png", true);
			});

			runStep("Agregar Negocio modal", statusByField, noteByField, failures, () -> {
				clickByVisibleText(page, "Agregar Negocio", timeoutMs);
				requireVisible(page, "Crear Nuevo Negocio", timeoutMs);
				requireVisible(page, "Nombre del Negocio", timeoutMs);
				requireVisible(page, "Tienes 2 de 3 negocios", timeoutMs);
				requireVisible(page, "Cancelar", timeoutMs);
				requireVisible(page, "Crear Negocio", timeoutMs);
				captureScreenshot(page, evidenceDir, "03_agregar_negocio_modal.png", true);

				Locator businessNameInput = firstVisibleLocator(timeoutMs, page.getByLabel(textPattern("Nombre del Negocio")),
						page.getByPlaceholder(textPattern("Nombre del Negocio")), page.locator("input").first());
				businessNameInput.click();
				waitForUi(page);
				businessNameInput.fill("Negocio Prueba Automatización");
				waitForUi(page);
				clickByVisibleText(page, "Cancelar", timeoutMs);
			});

			runStep("Administrar Negocios view", statusByField, noteByField, failures, () -> {
				ensureMiNegocioExpanded(page, timeoutMs);
				clickByVisibleText(page, "Administrar Negocios", timeoutMs);
				requireVisible(page, "Información General", timeoutMs);
				requireVisible(page, "Detalles de la Cuenta", timeoutMs);
				requireVisible(page, "Tus Negocios", timeoutMs);
				requireVisible(page, "Sección Legal", timeoutMs);
				captureScreenshot(page, evidenceDir, "04_administrar_negocios_view.png", true);
			});

			runStep("Información General", statusByField, noteByField, failures, () -> {
				requireVisible(page, googleEmail, timeoutMs);
				requireVisible(page, "BUSINESS PLAN", timeoutMs);
				requireVisible(page, "Cambiar Plan", timeoutMs);

				String sectionText = sectionText(page, "Información General");
				boolean hasVisibleNameLabel = hasVisibleText(page, "Nombre", 3000) || hasVisibleText(page, "Usuario", 3000)
						|| looksLikeNamePresent(sectionText, googleEmail);
				if (!hasVisibleNameLabel) {
					throw new AssertionError("User name is not clearly visible in 'Información General'.");
				}
			});

			runStep("Detalles de la Cuenta", statusByField, noteByField, failures, () -> {
				requireVisible(page, "Cuenta creada", timeoutMs);
				requireVisible(page, "Estado activo", timeoutMs);
				requireVisible(page, "Idioma seleccionado", timeoutMs);
			});

			runStep("Tus Negocios", statusByField, noteByField, failures, () -> {
				requireVisible(page, "Tus Negocios", timeoutMs);
				requireVisible(page, "Agregar Negocio", timeoutMs);
				requireVisible(page, "Tienes 2 de 3 negocios", timeoutMs);
				assertBusinessListVisible(page);
			});

			runStep("Términos y Condiciones", statusByField, noteByField, failures, () -> {
				String termsUrl = openLegalPageAndValidate(page, "Términos y Condiciones", "Términos y Condiciones",
						evidenceDir.resolve("08_terminos_y_condiciones.png"), timeoutMs);
				legalUrls.put("Términos y Condiciones", termsUrl);
			});

			runStep("Política de Privacidad", statusByField, noteByField, failures, () -> {
				String privacyUrl = openLegalPageAndValidate(page, "Política de Privacidad", "Política de Privacidad",
						evidenceDir.resolve("09_politica_de_privacidad.png"), timeoutMs);
				legalUrls.put("Política de Privacidad", privacyUrl);
			});
		}

		Path reportPath = writeFinalReport(evidenceDir, statusByField, noteByField, legalUrls, failures);
		boolean hasFailures = statusByField.values().stream().anyMatch("FAIL"::equalsIgnoreCase);
		if (hasFailures) {
			fail("One or more workflow validations failed. Report: " + reportPath.toAbsolutePath());
		}
	}

	private Map<String, String> initializeStatus() {
		Map<String, String> status = new LinkedHashMap<>();
		for (String field : REPORT_FIELDS) {
			status.put(field, "FAIL");
		}
		return status;
	}

	private boolean runStep(final String stepName, final Map<String, String> statusByField, final Map<String, String> noteByField,
			final List<String> failures, final CheckedRunnable runnable) {
		try {
			runnable.run();
			statusByField.put(stepName, "PASS");
			noteByField.put(stepName, "Validated successfully.");
			return true;
		} catch (Throwable t) {
			String message = compactError(t);
			statusByField.put(stepName, "FAIL");
			noteByField.put(stepName, message);
			failures.add(stepName + ": " + message);
			return false;
		}
	}

	private void markRemainingAsFailed(final Map<String, String> statusByField, final Map<String, String> noteByField,
			final List<String> failures, final String reason) {
		for (String field : REPORT_FIELDS) {
			if (!"PASS".equalsIgnoreCase(statusByField.get(field))) {
				statusByField.put(field, "FAIL");
				noteByField.put(field, reason);
				failures.add(field + ": " + reason);
			}
		}
	}

	private void loginWithGoogle(final Page page, final String googleEmail, final long timeoutMs) throws Exception {
		Locator loginButton = firstVisibleLocator(timeoutMs,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern("Sign in with Google"))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern("Iniciar sesión con Google"))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern("Continuar con Google"))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern("Google"))),
				page.getByText(textPattern("Sign in with Google")), page.getByText(textPattern("Iniciar sesión con Google")),
				page.getByText(textPattern("Continuar con Google")), page.getByText(textPattern("Google")));

		Page popup = null;
		try {
			popup = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(7000), () -> loginButton.click());
		} catch (PlaywrightException popupNotOpened) {
			loginButton.click();
		}
		waitForUi(page);

		if (popup != null) {
			waitForUi(popup);
			selectGoogleAccountIfVisible(popup, googleEmail, timeoutMs);
			waitForPageClose(popup, timeoutMs);
		}

		selectGoogleAccountIfVisible(page, googleEmail, timeoutMs);
		waitForUi(page);
	}

	private void selectGoogleAccountIfVisible(final Page candidatePage, final String googleEmail, final long timeoutMs)
			throws InterruptedException {
		if (candidatePage.isClosed()) {
			return;
		}

		Locator accountOption = candidatePage.getByText(textPattern(googleEmail)).first();
		long maxTime = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < maxTime) {
			if (candidatePage.isClosed()) {
				return;
			}
			try {
				if (accountOption.isVisible()) {
					accountOption.click();
					waitForUi(candidatePage);
					return;
				}
			} catch (PlaywrightException ignored) {
				return;
			}
			Thread.sleep(250);
		}
	}

	private void ensureMiNegocioExpanded(final Page page, final long timeoutMs) throws Exception {
		if (!hasVisibleText(page, "Administrar Negocios", 1500)) {
			clickByVisibleText(page, "Mi Negocio", timeoutMs);
			waitForUi(page);
		}
		if (!hasVisibleText(page, "Administrar Negocios", 1500)) {
			clickByVisibleText(page, "Negocio", timeoutMs);
			waitForUi(page);
		}
	}

	private String openLegalPageAndValidate(final Page appPage, final String linkText, final String headingText,
			final Path screenshotPath, final long timeoutMs) throws Exception {
		String appUrlBeforeClick = appPage.url();
		Page legalPage = appPage;
		Page popup = null;

		try {
			popup = appPage.waitForPopup(new Page.WaitForPopupOptions().setTimeout(7000),
					() -> clickByVisibleTextUnchecked(appPage, linkText, timeoutMs));
		} catch (PlaywrightException popupNotOpened) {
			clickByVisibleText(appPage, linkText, timeoutMs);
		}

		if (popup != null) {
			legalPage = popup;
		}

		waitForUi(legalPage);
		requireVisible(legalPage, headingText, timeoutMs);

		String bodyText = legalPage.locator("body").innerText();
		if (bodyText == null || bodyText.trim().length() < 120) {
			throw new AssertionError("Legal content text is too short or not visible for '" + headingText + "'.");
		}

		legalPage.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(true));
		String finalUrl = legalPage.url();

		if (popup != null && !popup.isClosed()) {
			popup.close();
			appPage.bringToFront();
			waitForUi(appPage);
			return finalUrl;
		}

		try {
			appPage.goBack();
			waitForUi(appPage);
		} catch (PlaywrightException ignored) {
			appPage.navigate(appUrlBeforeClick);
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private void clickByVisibleText(final Page page, final String text, final long timeoutMs) throws Exception {
		Locator locator = firstVisibleLocator(timeoutMs,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern(text))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(textPattern(text))),
				page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(textPattern(text))),
				page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(textPattern(text))),
				page.getByText(textPattern(text)));
		locator.click();
		waitForUi(page);
	}

	private void clickByVisibleTextUnchecked(final Page page, final String text, final long timeoutMs) {
		try {
			clickByVisibleText(page, text, timeoutMs);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void requireVisible(final Page page, final String text, final long timeoutMs) throws Exception {
		Locator locator = firstVisibleLocator(timeoutMs, page.getByText(textPattern(text)),
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(textPattern(text))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern(text))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(textPattern(text))));
		locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
	}

	private boolean hasVisibleText(final Page page, final String text, final long timeoutMs) throws InterruptedException {
		Locator locator = page.getByText(textPattern(text)).first();
		long maxTime = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < maxTime) {
			try {
				if (locator.isVisible()) {
					return true;
				}
			} catch (PlaywrightException ignored) {
				return false;
			}
			Thread.sleep(200);
		}
		return false;
	}

	private void requireSidebarVisible(final Page page, final long timeoutMs) throws Exception {
		Locator sidebar = firstVisibleLocator(timeoutMs, page.locator("aside"), page.locator("nav"));
		sidebar.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
	}

	private void assertBusinessListVisible(final Page page) throws Exception {
		Locator section = firstVisibleLocator(10000, page.locator("section:has-text('Tus Negocios')"),
				page.locator("div:has-text('Tus Negocios')"), page.locator("article:has-text('Tus Negocios')"));
		String sectionText = section.innerText();
		long candidateRows = section.locator("li, tr, [role='row'], [class*='business'], [class*='negocio']").count();
		long nonEmptyLines = Arrays.stream(sectionText.split("\\R")).map(String::trim).filter(line -> !line.isEmpty()).count();
		if (candidateRows < 1 && nonEmptyLines < 4) {
			throw new AssertionError("Business list is not clearly visible in 'Tus Negocios'.");
		}
	}

	private String sectionText(final Page page, final String title) throws Exception {
		Locator section = firstVisibleLocator(10000, page.locator("section:has-text('" + title + "')"),
				page.locator("div:has-text('" + title + "')"), page.locator("article:has-text('" + title + "')"));
		return section.innerText();
	}

	private boolean looksLikeNamePresent(final String text, final String email) {
		return Arrays.stream(text.split("\\R")).map(String::trim).filter(line -> !line.isEmpty())
				.filter(line -> !line.equalsIgnoreCase("Información General")).filter(line -> !line.contains("@"))
				.filter(line -> !line.equalsIgnoreCase("BUSINESS PLAN")).anyMatch(line -> line.matches(".*[A-Za-zÁÉÍÓÚÑáéíóúñ]{3,}.*"));
	}

	private Locator firstVisibleLocator(final long timeoutMs, final Locator... candidates) throws Exception {
		long maxTime = System.currentTimeMillis() + timeoutMs;
		PlaywrightException lastException = null;

		while (System.currentTimeMillis() < maxTime) {
			for (Locator candidate : candidates) {
				try {
					Locator first = candidate.first();
					if (first.isVisible()) {
						return first;
					}
				} catch (PlaywrightException e) {
					lastException = e;
				}
			}
			Thread.sleep(200);
		}

		throw new AssertionError("Could not locate a visible element before timeout.", lastException);
	}

	private void waitForUi(final Page page) throws InterruptedException {
		if (page.isClosed()) {
			return;
		}

		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(8000));
		} catch (PlaywrightException ignored) {
			// Some SPA transitions do not trigger this state; continue.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8000));
		} catch (PlaywrightException ignored) {
			// Network can stay active due to polling; continue after a short pause.
		}
		Thread.sleep(450);
	}

	private void waitForPageClose(final Page page, final long timeoutMs) throws InterruptedException {
		long maxTime = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < maxTime) {
			if (page.isClosed()) {
				return;
			}
			Thread.sleep(200);
		}
	}

	private void captureScreenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage)
			throws IOException {
		page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName)).setFullPage(fullPage));
	}

	private Path writeFinalReport(final Path evidenceDir, final Map<String, String> statusByField,
			final Map<String, String> noteByField, final Map<String, String> legalUrls, final List<String> failures)
			throws IOException {
		Path reportPath = evidenceDir.resolve("final_report.md");
		StringBuilder sb = new StringBuilder();

		sb.append("# ").append(TEST_NAME).append(" - Final Report").append(System.lineSeparator()).append(System.lineSeparator());
		sb.append("- Generated at UTC: ").append(Instant.now()).append(System.lineSeparator());
		sb.append("- Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator())
				.append(System.lineSeparator());

		sb.append("## Step Status").append(System.lineSeparator()).append(System.lineSeparator());
		for (String field : REPORT_FIELDS) {
			sb.append("- ").append(field).append(": ").append(statusByField.getOrDefault(field, "FAIL"));
			String note = noteByField.get(field);
			if (note != null && !note.isBlank()) {
				sb.append(" (").append(note).append(")");
			}
			sb.append(System.lineSeparator());
		}
		sb.append(System.lineSeparator());

		sb.append("## Legal URLs").append(System.lineSeparator()).append(System.lineSeparator());
		sb.append("- Términos y Condiciones: ").append(legalUrls.getOrDefault("Términos y Condiciones", "N/A"))
				.append(System.lineSeparator());
		sb.append("- Política de Privacidad: ").append(legalUrls.getOrDefault("Política de Privacidad", "N/A"))
				.append(System.lineSeparator()).append(System.lineSeparator());

		sb.append("## Failures").append(System.lineSeparator()).append(System.lineSeparator());
		if (failures.isEmpty()) {
			sb.append("- None").append(System.lineSeparator());
		} else {
			for (String failure : failures) {
				sb.append("- ").append(failure).append(System.lineSeparator());
			}
		}

		Files.writeString(reportPath, sb.toString(), StandardCharsets.UTF_8);
		return reportPath;
	}

	private Path createEvidenceDirectory() throws IOException {
		String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
		Path directory = Paths.get("target", "e2e-evidence", TEST_NAME + "_" + timestamp);
		Files.createDirectories(directory);
		return directory;
	}

	private String compactError(Throwable throwable) {
		String message = throwable.getMessage();
		if (message == null || message.isBlank()) {
			message = throwable.getClass().getSimpleName();
		}
		return Arrays.stream(message.split("\\R")).map(String::trim).filter(line -> !line.isEmpty()).limit(5)
				.collect(Collectors.joining(" | "));
	}

	private Pattern textPattern(String value) {
		return Pattern.compile(".*" + Pattern.quote(value) + ".*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
