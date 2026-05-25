package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
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

public class SaleadsMiNegocioWorkflowTest {

	private static final double DEFAULT_TIMEOUT_MS = 15000;
	private static final DateTimeFormatter ARTIFACT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final List<String> FINAL_REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad");

	@Test
	public void saleads_mi_negocio_full_test() throws IOException {
		final boolean enabled = getBooleanConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false);
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true (or -Dsaleads.e2e.enabled=true) to run this E2E workflow.",
				enabled);

		final String loginUrl = firstNonBlank(
				System.getProperty("saleads.login.url"),
				System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL (or -Dsaleads.login.url=...) for the active environment login page.",
				loginUrl != null && !loginUrl.isBlank());

		final String googleAccount = firstNonBlank(
				System.getProperty("saleads.google.account"),
				System.getenv("SALEADS_GOOGLE_ACCOUNT"),
				"juanlucasbarbiergarzon@gmail.com");
		final String expectedUserName = firstNonBlank(
				System.getProperty("saleads.expected.user.name"),
				System.getenv("SALEADS_EXPECTED_USER_NAME"));
		final boolean headless = getBooleanConfig("saleads.headless", "SALEADS_HEADLESS", true);

		final Path artifactsDir = Files.createDirectories(Path.of("target", "saleads-e2e-artifacts",
				ARTIFACT_TIME_FORMAT.format(LocalDateTime.now())));

		final Map<String, String> report = new LinkedHashMap<>();
		for (final String field : FINAL_REPORT_FIELDS) {
			report.put(field, "FAIL - Step was not executed.");
		}

		final Map<String, String> legalUrls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(250));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1100));
			final Page page = context.newPage();

			page.navigate(loginUrl);
			waitForUiLoad(page);

			runStep(report, "Login", () -> loginWithGoogle(page, googleAccount, artifactsDir));
			runStep(report, "Mi Negocio menu", () -> validateMiNegocioMenu(page, artifactsDir));
			runStep(report, "Agregar Negocio modal", () -> validateAgregarNegocioModal(page, artifactsDir));
			runStep(report, "Administrar Negocios view", () -> openAdministrarNegocios(page, artifactsDir));
			runStep(report, "Información General",
					() -> validateInformacionGeneral(page, googleAccount, expectedUserName));
			runStep(report, "Detalles de la Cuenta", () -> validateDetallesDeLaCuenta(page));
			runStep(report, "Tus Negocios", () -> validateTusNegocios(page));
			runStep(report, "Términos y Condiciones",
					() -> validateLegalLink(page, "Términos y Condiciones", "Términos y Condiciones", "08-terminos",
							artifactsDir, legalUrls));
			runStep(report, "Política de Privacidad",
					() -> validateLegalLink(page, "Política de Privacidad", "Política de Privacidad", "09-politica",
							artifactsDir, legalUrls));

			writeFinalReport(artifactsDir, report, legalUrls);
		}

		final List<String> failedFields = new ArrayList<>();
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			if (!entry.getValue().startsWith("PASS")) {
				failedFields.add(entry.getKey() + " -> " + entry.getValue());
			}
		}

		if (!failedFields.isEmpty()) {
			Assert.fail("One or more SaleADS Mi Negocio validations failed:\n" + String.join("\n", failedFields));
		}
	}

	private void loginWithGoogle(final Page page, final String googleAccount, final Path artifactsDir) throws IOException {
		final Locator signInButton = resolveClickableByText(page,
				Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google"));
		assertVisible(signInButton, "Google login button");

		final Page popup = clickAndMaybeCapturePopup(page, signInButton);
		if (popup != null) {
			handleGoogleAccountSelection(popup, googleAccount);
			try {
				popup.waitForClose(new Page.WaitForCloseOptions().setTimeout(DEFAULT_TIMEOUT_MS));
			} catch (final PlaywrightException ignored) {
				// Some environments keep the Google tab open after auth handoff.
				popup.close(new Page.CloseOptions().setRunBeforeUnload(true));
			}
		} else {
			handleGoogleAccountSelection(page, googleAccount);
		}

		waitForVisibleTextAny(page, Arrays.asList("Negocio", "Mi Negocio", "Dashboard", "Panel"), 120000);
		waitForVisibleText(page, "Negocio", 120000);
		screenshot(page, artifactsDir, "01-dashboard-loaded", true);
	}

	private void validateMiNegocioMenu(final Page page, final Path artifactsDir) throws IOException {
		ensureMiNegocioExpanded(page);
		screenshot(page, artifactsDir, "02-mi-negocio-menu-expanded", false);
	}

	private void validateAgregarNegocioModal(final Page page, final Path artifactsDir) throws IOException {
		ensureMiNegocioExpanded(page);
		clickByText(page, Arrays.asList("Agregar Negocio"), DEFAULT_TIMEOUT_MS);
		waitForVisibleText(page, "Crear Nuevo Negocio", DEFAULT_TIMEOUT_MS);
		waitForVisibleText(page, "Nombre del Negocio", DEFAULT_TIMEOUT_MS);
		waitForVisibleText(page, "Tienes 2 de 3 negocios", DEFAULT_TIMEOUT_MS);
		waitForVisibleText(page, "Cancelar", DEFAULT_TIMEOUT_MS);
		waitForVisibleText(page, "Crear Negocio", DEFAULT_TIMEOUT_MS);

		final Locator nombreNegocioInput = page.getByLabel("Nombre del Negocio").first();
		if (isVisible(nombreNegocioInput, 2000)) {
			nombreNegocioInput.fill("Negocio Prueba Automatización");
		}

		screenshot(page, artifactsDir, "03-agregar-negocio-modal", false);
		clickByText(page, Arrays.asList("Cancelar"), DEFAULT_TIMEOUT_MS);
	}

	private void openAdministrarNegocios(final Page page, final Path artifactsDir) throws IOException {
		ensureMiNegocioExpanded(page);
		clickByText(page, Arrays.asList("Administrar Negocios"), DEFAULT_TIMEOUT_MS);
		waitForVisibleText(page, "Información General", DEFAULT_TIMEOUT_MS);
		waitForVisibleText(page, "Detalles de la Cuenta", DEFAULT_TIMEOUT_MS);
		waitForVisibleText(page, "Tus Negocios", DEFAULT_TIMEOUT_MS);
		waitForVisibleTextAny(page, Arrays.asList("Sección Legal", "Seccion Legal"), DEFAULT_TIMEOUT_MS);
		screenshot(page, artifactsDir, "04-administrar-negocios", true);
	}

	private void validateInformacionGeneral(final Page page, final String googleAccount, final String expectedUserName) {
		waitForVisibleText(page, "Información General", DEFAULT_TIMEOUT_MS);
		waitForVisibleText(page, googleAccount, DEFAULT_TIMEOUT_MS);
		waitForVisibleText(page, "BUSINESS PLAN", DEFAULT_TIMEOUT_MS);
		waitForVisibleText(page, "Cambiar Plan", DEFAULT_TIMEOUT_MS);

		if (expectedUserName != null && !expectedUserName.isBlank()) {
			waitForVisibleText(page, expectedUserName, DEFAULT_TIMEOUT_MS);
		} else {
			final String content = page.content().toLowerCase(Locale.ROOT);
			assertTrue("Expected a likely user identity token in Información General.",
					content.contains("juan") || content.contains("lucas") || content.contains("barbier")
							|| content.contains("garzon") || content.contains(googleAccount.toLowerCase(Locale.ROOT)));
		}
	}

	private void validateDetallesDeLaCuenta(final Page page) {
		waitForVisibleText(page, "Detalles de la Cuenta", DEFAULT_TIMEOUT_MS);
		waitForVisibleText(page, "Cuenta creada", DEFAULT_TIMEOUT_MS);
		waitForVisibleText(page, "Estado activo", DEFAULT_TIMEOUT_MS);
		waitForVisibleText(page, "Idioma seleccionado", DEFAULT_TIMEOUT_MS);
	}

	private void validateTusNegocios(final Page page) {
		waitForVisibleText(page, "Tus Negocios", DEFAULT_TIMEOUT_MS);
		waitForVisibleText(page, "Agregar Negocio", DEFAULT_TIMEOUT_MS);
		waitForVisibleText(page, "Tienes 2 de 3 negocios", DEFAULT_TIMEOUT_MS);
	}

	private void validateLegalLink(final Page page, final String linkText, final String headingText,
			final String screenshotName, final Path artifactsDir, final Map<String, String> legalUrls) throws IOException {
		final Locator link = resolveClickableByText(page, Arrays.asList(linkText));
		assertVisible(link, linkText + " link");

		final Page legalPage = clickAndMaybeCapturePopup(page, link);
		final Page activePage = legalPage == null ? page : legalPage;

		waitForVisibleText(activePage, headingText, 30000);
		final String pageText = activePage.textContent("body");
		assertTrue("Expected legal page to include non-trivial content for " + headingText,
				pageText != null && pageText.trim().length() > 200);

		screenshot(activePage, artifactsDir, screenshotName, true);
		legalUrls.put(headingText, activePage.url());

		if (legalPage != null) {
			legalPage.close(new Page.CloseOptions().setRunBeforeUnload(true));
			page.bringToFront();
			waitForUiLoad(page);
		} else {
			page.goBack();
			waitForUiLoad(page);
		}
	}

	private void handleGoogleAccountSelection(final Page page, final String googleAccount) {
		waitForUiLoad(page);
		final Locator accountOption = page.getByText(googleAccount).first();
		if (isVisible(accountOption, 10000)) {
			accountOption.click();
			waitForUiLoad(page);
			return;
		}

		final Locator accountRole = page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(googleAccount), Pattern.CASE_INSENSITIVE)))
				.first();
		if (isVisible(accountRole, 4000)) {
			accountRole.click();
			waitForUiLoad(page);
		}
	}

	private void clickByText(final Page page, final List<String> texts, final double timeoutMs) {
		final Locator locator = resolveClickableByText(page, texts);
		assertVisible(locator, "click target: " + String.join(", ", texts));
		locator.click(new Locator.ClickOptions().setTimeout(timeoutMs));
		waitForUiLoad(page);
	}

	private void ensureMiNegocioExpanded(final Page page) {
		if (isVisible(page.getByText("Agregar Negocio").first(), 1500)
				&& isVisible(page.getByText("Administrar Negocios").first(), 1500)) {
			return;
		}

		if (!isVisible(page.getByText("Mi Negocio").first(), 1500)) {
			clickByText(page, Arrays.asList("Negocio"), DEFAULT_TIMEOUT_MS);
		}

		clickByText(page, Arrays.asList("Mi Negocio"), DEFAULT_TIMEOUT_MS);
		if (isVisible(page.getByText("Agregar Negocio").first(), 1500)
				&& isVisible(page.getByText("Administrar Negocios").first(), 1500)) {
			return;
		}

		// Some sidebars require a second click due to focus/state transitions.
		clickByText(page, Arrays.asList("Mi Negocio"), DEFAULT_TIMEOUT_MS);
		waitForVisibleText(page, "Agregar Negocio", DEFAULT_TIMEOUT_MS);
		waitForVisibleText(page, "Administrar Negocios", DEFAULT_TIMEOUT_MS);
	}

	private Locator resolveClickableByText(final Page page, final List<String> texts) {
		for (final String text : texts) {
			final List<Locator> candidates = Arrays.asList(
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text)).first(),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text)).first(),
					page.getByText(text, new Page.GetByTextOptions().setExact(true)).first(),
					page.getByText(text).first());

			for (final Locator candidate : candidates) {
				if (isVisible(candidate, 1500)) {
					return candidate;
				}
			}
		}

		throw new AssertionError("Could not resolve a visible element for any of: " + String.join(", ", texts));
	}

	private Page clickAndMaybeCapturePopup(final Page page, final Locator locator) {
		try {
			final Page popup = page.waitForPopup(() -> locator.click(), new Page.WaitForPopupOptions().setTimeout(8000));
			waitForUiLoad(popup);
			return popup;
		} catch (final PlaywrightException ignored) {
			waitForUiLoad(page);
			return null;
		}
	}

	private boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs).setState(WaitForSelectorState.VISIBLE));
			return true;
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void assertVisible(final Locator locator, final String name) {
		assertTrue("Expected visible element: " + name, isVisible(locator, DEFAULT_TIMEOUT_MS));
	}

	private void waitForVisibleText(final Page page, final String text, final double timeoutMs) {
		final Locator locator = page.getByText(text).first();
		assertTrue("Expected visible text: " + text, isVisible(locator, timeoutMs));
	}

	private void waitForVisibleTextAny(final Page page, final List<String> texts, final double timeoutMs) {
		for (final String text : texts) {
			if (isVisible(page.getByText(text).first(), timeoutMs / Math.max(texts.size(), 1))) {
				return;
			}
		}

		throw new AssertionError("Expected at least one visible text from: " + String.join(", ", texts));
	}

	private void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(20000));
		} catch (final PlaywrightException ignored) {
			// Some app routes don't trigger full load events.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (final PlaywrightException ignored) {
			// SPA screens may keep active connections.
		}
		page.waitForTimeout(1000);
	}

	private void screenshot(final Page page, final Path artifactsDir, final String name, final boolean fullPage)
			throws IOException {
		final Path screenshotPath = artifactsDir.resolve(name + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	private void runStep(final Map<String, String> report, final String key, final CheckedStep step) {
		try {
			step.run();
			report.put(key, "PASS");
		} catch (final Throwable throwable) {
			final String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName()
					: throwable.getMessage();
			report.put(key, "FAIL - " + message);
		}
	}

	private void writeFinalReport(final Path artifactsDir, final Map<String, String> report,
			final Map<String, String> legalUrls) throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		builder.append("================================").append(System.lineSeparator()).append(System.lineSeparator());

		for (final String field : FINAL_REPORT_FIELDS) {
			builder.append(field).append(": ").append(report.get(field)).append(System.lineSeparator());
		}

		builder.append(System.lineSeparator()).append("Captured legal URLs").append(System.lineSeparator());
		builder.append("-------------------").append(System.lineSeparator());
		if (legalUrls.isEmpty()) {
			builder.append("No legal URLs captured.").append(System.lineSeparator());
		} else {
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				builder.append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		Files.writeString(artifactsDir.resolve("final-report.txt"), builder.toString(), StandardCharsets.UTF_8);
	}

	private boolean getBooleanConfig(final String propertyName, final String envName, final boolean defaultValue) {
		final String configured = firstNonBlank(System.getProperty(propertyName), System.getenv(envName));
		return configured == null ? defaultValue : Boolean.parseBoolean(configured);
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	@FunctionalInterface
	private interface CheckedStep {
		void run() throws Exception;
	}
}
