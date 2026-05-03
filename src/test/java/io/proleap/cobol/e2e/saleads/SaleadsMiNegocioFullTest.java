package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

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

/**
 * End-to-end browser validation for the complete SaleADS "Mi Negocio" workflow.
 */
public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Path ARTIFACTS_DIR = Paths.get("target", "e2e-artifacts", TEST_NAME);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final int SHORT_TIMEOUT_MS = 3_000;
	private static final int UI_TIMEOUT_MS = 20_000;

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		assumeTrue("SaleADS E2E test is disabled. Set -Dsaleads.e2e.enabled=true (or SALEADS_E2E_ENABLED=true) to run.",
				isE2eEnabled());

		Files.createDirectories(ARTIFACTS_DIR);

		final String loginUrl = resolveLoginUrl();
		if (loginUrl == null) {
			throw new IllegalStateException(
					"Missing login URL. Provide -Dsaleads.loginUrl=<url> or environment variable SALEADS_LOGIN_URL.");
		}

		final LegalValidationEvidence termsEvidence = new LegalValidationEvidence();
		final LegalValidationEvidence privacyEvidence = new LegalValidationEvidence();

		try (Playwright playwright = Playwright.create();
				Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
						.setHeadless(Boolean.parseBoolean(System.getProperty("saleads.headless", "true"))));
				BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900))) {

			final Page appPage = context.newPage();

			runStep("Login", appPage, () -> loginWithGoogle(appPage, loginUrl));
			runStep("Mi Negocio menu", appPage, () -> openMiNegocioMenu(appPage));
			runStep("Agregar Negocio modal", appPage, () -> validateAgregarNegocioModal(appPage));
			runStep("Administrar Negocios view", appPage, () -> openAdministrarNegociosView(appPage));
			runStep("Información General", appPage, () -> validateInformacionGeneral(appPage));
			runStep("Detalles de la Cuenta", appPage, () -> validateDetallesDeCuenta(appPage));
			runStep("Tus Negocios", appPage, () -> validateTusNegocios(appPage));
			runStep("Términos y Condiciones", appPage,
					() -> validateLegalLink(appPage, "Términos y Condiciones", "08-terminos-y-condiciones", termsEvidence));
			runStep("Política de Privacidad", appPage,
					() -> validateLegalLink(appPage, "Política de Privacidad", "09-politica-de-privacidad", privacyEvidence));
		} finally {
			writeFinalReport(termsEvidence, privacyEvidence);
		}

		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			if (!entry.getValue().passed) {
				failedSteps.add(entry.getKey() + " -> " + entry.getValue().message);
			}
		}

		assertTrue("One or more SaleADS validations failed. See " + ARTIFACTS_DIR.resolve("final-report.md") + " :: "
				+ failedSteps, failedSteps.isEmpty());
	}

	private void loginWithGoogle(final Page appPage, final String loginUrl) {
		appPage.navigate(loginUrl);
		waitForUi(appPage);

		final Locator loginButton = findFirstVisible(appPage,
				roleButtonByName(appPage, ".*(sign\\s*in\\s*with\\s*google|iniciar\\s*sesi[oó]n\\s*con\\s*google|continuar\\s*con\\s*google|google).*"),
				roleLinkByName(appPage, ".*(sign\\s*in\\s*with\\s*google|iniciar\\s*sesi[oó]n\\s*con\\s*google|continuar\\s*con\\s*google|google).*"),
				textByPattern(appPage, ".*(sign\\s*in\\s*with\\s*google|iniciar\\s*sesi[oó]n\\s*con\\s*google|continuar\\s*con\\s*google).*"),
				textByPattern(appPage, ".*google.*"));

		final int initialPageCount = appPage.context().pages().size();
		clickAndWaitForUi(appPage, loginButton);
		Page googlePage = waitForNewPage(appPage.context(), initialPageCount, 8_000);

		if (googlePage != null) {
			waitForUi(googlePage);
			selectGoogleAccountIfVisible(googlePage);

			waitForClosureOrTimeout(googlePage, 45_000);
		} else {
			selectGoogleAccountIfVisible(appPage);
		}

		appPage.bringToFront();
		waitForUi(appPage);

		final Locator sidebar = findFirstVisible(appPage, appPage.locator("aside"), appPage.getByRole(AriaRole.NAVIGATION),
				textByPattern(appPage, ".*(mi\\s*negocio|negocio).*"));
		sidebar.waitFor(new Locator.WaitForOptions().setTimeout(60_000));

		takeScreenshot(appPage, "01-dashboard-loaded.png", true);
	}

	private void openMiNegocioMenu(final Page appPage) {
		final Locator negocioEntry = findFirstVisible(appPage, roleButtonByName(appPage, ".*negocio.*"),
				roleLinkByName(appPage, ".*negocio.*"), textByPattern(appPage, ".*negocio.*"));
		clickAndWaitForUi(appPage, negocioEntry);

		final Locator miNegocioEntry = findFirstVisible(appPage, roleButtonByName(appPage, ".*mi\\s*negocio.*"),
				roleLinkByName(appPage, ".*mi\\s*negocio.*"), textByPattern(appPage, ".*mi\\s*negocio.*"));
		clickAndWaitForUi(appPage, miNegocioEntry);

		assertVisible(findFirstVisible(appPage, textByPattern(appPage, ".*agregar\\s*negocio.*"),
				roleLinkByName(appPage, ".*agregar\\s*negocio.*"), roleButtonByName(appPage, ".*agregar\\s*negocio.*")),
				"Expected 'Agregar Negocio' in expanded submenu.");

		assertVisible(
				findFirstVisible(appPage, textByPattern(appPage, ".*administrar\\s*negocios.*"),
						roleLinkByName(appPage, ".*administrar\\s*negocios.*"), roleButtonByName(appPage, ".*administrar\\s*negocios.*")),
				"Expected 'Administrar Negocios' in expanded submenu.");

		takeScreenshot(appPage, "02-mi-negocio-menu-expanded.png", false);
	}

	private void validateAgregarNegocioModal(final Page appPage) {
		expandMiNegocioIfNeeded(appPage);

		final Locator agregarNegocio = findFirstVisible(appPage, textByPattern(appPage, ".*agregar\\s*negocio.*"),
				roleLinkByName(appPage, ".*agregar\\s*negocio.*"), roleButtonByName(appPage, ".*agregar\\s*negocio.*"));
		clickAndWaitForUi(appPage, agregarNegocio);

		final Locator modalTitle = findFirstVisible(appPage, textByPattern(appPage, "\\s*crear\\s+nuevo\\s+negocio\\s*"));
		assertVisible(modalTitle, "Expected modal title 'Crear Nuevo Negocio'.");

		final Locator businessNameInput = findFirstVisible(appPage,
				appPage.locator("input[placeholder*='Nombre'][placeholder*='Negocio']"),
				appPage.locator("input[aria-label*='Nombre'][aria-label*='Negocio']"),
				appPage.locator("label:has-text('Nombre del Negocio')").locator("xpath=following::input[1]"),
				appPage.locator("[role='dialog'] input"));
		assertVisible(businessNameInput, "Expected input field 'Nombre del Negocio'.");

		assertVisible(findFirstVisible(appPage, textByPattern(appPage, ".*tienes\\s+2\\s+de\\s+3\\s+negocios.*")),
				"Expected text 'Tienes 2 de 3 negocios'.");
		assertVisible(findFirstVisible(appPage, roleButtonByName(appPage, "\\s*cancelar\\s*"),
				textByPattern(appPage, "\\s*cancelar\\s*")), "Expected button 'Cancelar'.");
		assertVisible(findFirstVisible(appPage, roleButtonByName(appPage, ".*crear\\s+negocio.*"),
				textByPattern(appPage, ".*crear\\s+negocio.*")), "Expected button 'Crear Negocio'.");

		takeScreenshot(appPage, "03-agregar-negocio-modal.png", false);

		businessNameInput.click();
		businessNameInput.fill("Negocio Prueba Automatización");
		clickAndWaitForUi(appPage,
				findFirstVisible(appPage, roleButtonByName(appPage, "\\s*cancelar\\s*"), textByPattern(appPage, "\\s*cancelar\\s*")));

		assertNotVisible(modalTitle, "Expected 'Crear Nuevo Negocio' modal to close after pressing Cancelar.");
	}

	private void openAdministrarNegociosView(final Page appPage) {
		expandMiNegocioIfNeeded(appPage);

		final Locator administrarNegocios = findFirstVisible(appPage, textByPattern(appPage, ".*administrar\\s*negocios.*"),
				roleLinkByName(appPage, ".*administrar\\s*negocios.*"), roleButtonByName(appPage, ".*administrar\\s*negocios.*"));
		clickAndWaitForUi(appPage, administrarNegocios);

		assertVisible(findFirstVisible(appPage, textByPattern(appPage, "\\s*informaci[oó]n\\s+general\\s*")),
				"Expected section 'Información General'.");
		assertVisible(findFirstVisible(appPage, textByPattern(appPage, "\\s*detalles\\s+de\\s+la\\s+cuenta\\s*")),
				"Expected section 'Detalles de la Cuenta'.");
		assertVisible(findFirstVisible(appPage, textByPattern(appPage, "\\s*tus\\s+negocios\\s*")),
				"Expected section 'Tus Negocios'.");
		assertVisible(findFirstVisible(appPage, textByPattern(appPage, "\\s*secci[oó]n\\s+legal\\s*")),
				"Expected section 'Sección Legal'.");

		takeScreenshot(appPage, "04-administrar-negocios-view.png", true);
	}

	private void validateInformacionGeneral(final Page appPage) {
		assertVisible(findFirstVisible(appPage, textByPattern(appPage, "\\s*informaci[oó]n\\s+general\\s*")),
				"'Información General' section should be visible.");
		assertVisible(findFirstVisible(appPage, textByPattern(appPage, ".*business\\s+plan.*")),
				"Expected text 'BUSINESS PLAN'.");
		assertVisible(findFirstVisible(appPage, roleButtonByName(appPage, ".*cambiar\\s+plan.*"),
				textByPattern(appPage, ".*cambiar\\s+plan.*")), "Expected button 'Cambiar Plan'.");

		final String pageText = appPage.locator("body").innerText();
		assertTrue("Expected user email to be visible.", EMAIL_PATTERN.matcher(pageText).find());
		assertTrue("Expected a user name-like text to be visible.", containsLikelyUserName(pageText));
	}

	private void validateDetallesDeCuenta(final Page appPage) {
		assertVisible(findFirstVisible(appPage, textByPattern(appPage, ".*cuenta\\s+creada.*")),
				"Expected 'Cuenta creada' text.");
		assertVisible(findFirstVisible(appPage, textByPattern(appPage, ".*estado\\s+activo.*")),
				"Expected 'Estado activo' text.");
		assertVisible(findFirstVisible(appPage, textByPattern(appPage, ".*idioma\\s+seleccionado.*")),
				"Expected 'Idioma seleccionado' text.");
	}

	private void validateTusNegocios(final Page appPage) {
		final Locator tusNegociosHeading = findFirstVisible(appPage, textByPattern(appPage, "\\s*tus\\s+negocios\\s*"));
		assertVisible(tusNegociosHeading, "'Tus Negocios' heading should be visible.");

		assertVisible(findFirstVisible(appPage, roleButtonByName(appPage, ".*agregar\\s+negocio.*"),
				textByPattern(appPage, ".*agregar\\s+negocio.*")), "Expected 'Agregar Negocio' button.");
		assertVisible(findFirstVisible(appPage, textByPattern(appPage, ".*tienes\\s+2\\s+de\\s+3\\s+negocios.*")),
				"Expected text 'Tienes 2 de 3 negocios'.");

		final String pageText = appPage.locator("body").innerText();
		assertTrue("Expected visible business list/content in 'Tus Negocios'.", containsLikelyBusinessListContent(pageText));
	}

	private void validateLegalLink(final Page appPage, final String linkText, final String screenshotName,
			final LegalValidationEvidence evidence) {
		evidence.finalUrl = "N/A";

		final Locator legalLink = findFirstVisible(appPage, roleLinkByName(appPage, ".*" + Pattern.quote(linkText) + ".*"),
				textByPattern(appPage, ".*" + Pattern.quote(linkText) + ".*"), roleButtonByName(appPage, ".*" + Pattern.quote(linkText) + ".*"));

		final String appUrlBeforeNavigation = appPage.url();
		final int initialPageCount = appPage.context().pages().size();
		clickAndWaitForUi(appPage, legalLink);

		Page destinationPage = waitForNewPage(appPage.context(), initialPageCount, 7_000);
		final boolean openedPopup = destinationPage != null;
		if (!openedPopup) {
			destinationPage = appPage;
		}

		waitForUi(destinationPage);
		assertVisible(findFirstVisible(destinationPage, headingByName(destinationPage, ".*" + Pattern.quote(linkText) + ".*"),
				textByPattern(destinationPage, ".*" + Pattern.quote(linkText) + ".*")),
				"Expected heading '" + linkText + "' on legal page.");

		final String legalBody = destinationPage.locator("body").innerText();
		assertTrue("Expected visible legal content text for '" + linkText + "'.", legalBody.trim().length() > 200);

		takeScreenshot(destinationPage, screenshotName + ".png", true);
		evidence.finalUrl = destinationPage.url();

		if (openedPopup && destinationPage != appPage) {
			destinationPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			try {
				appPage.goBack();
				waitForUi(appPage);
			} catch (final PlaywrightException noHistory) {
				appPage.navigate(appUrlBeforeNavigation);
				waitForUi(appPage);
			}
		}
	}

	private void selectGoogleAccountIfVisible(final Page page) {
		clickIfVisible(page, textByPattern(page, ".*" + Pattern.quote(GOOGLE_ACCOUNT_EMAIL) + ".*"));
		waitForUi(page);

		clickIfVisible(page, roleButtonByName(page, "\\s*(siguiente|next|continuar|continue)\\s*"));
		clickIfVisible(page, roleButtonByName(page, "\\s*(permitir|allow|aceptar)\\s*"));
		waitForUi(page);
	}

	private void expandMiNegocioIfNeeded(final Page page) {
		if (isVisible(textByPattern(page, ".*agregar\\s*negocio.*")) && isVisible(textByPattern(page, ".*administrar\\s*negocios.*"))) {
			return;
		}

		clickAndWaitForUi(page, findFirstVisible(page, roleButtonByName(page, ".*mi\\s*negocio.*"),
				roleLinkByName(page, ".*mi\\s*negocio.*"), textByPattern(page, ".*mi\\s*negocio.*")));
	}

	private void runStep(final String label, final Page page, final CheckedRunnable stepAction) {
		try {
			stepAction.run();
			stepResults.put(label, StepResult.pass("PASS"));
		} catch (final AssertionError | Exception ex) {
			stepResults.put(label, StepResult.fail(ex.getMessage()));
			try {
				takeScreenshot(page, "failure-" + slugify(label) + ".png", true);
			} catch (final Exception ignored) {
				// Failure screenshots are best-effort evidence.
			}
		}
	}

	private void writeFinalReport(final LegalValidationEvidence termsEvidence, final LegalValidationEvidence privacyEvidence)
			throws IOException {
		final List<String> orderedFields = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
				"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
				"Términos y Condiciones", "Política de Privacidad");

		final StringBuilder report = new StringBuilder();
		report.append("# ").append(TEST_NAME).append('\n').append('\n');
		report.append("Generated at: ").append(DateTimeFormatter.ISO_INSTANT.format(Instant.now())).append('\n').append('\n');
		report.append("## Final Report (PASS/FAIL)\n");

		for (final String field : orderedFields) {
			final StepResult result = stepResults.getOrDefault(field, StepResult.fail("NOT EXECUTED"));
			report.append("- ").append(field).append(": ").append(result.passed ? "PASS" : "FAIL");
			if (result.message != null && !result.message.isBlank() && !"PASS".equals(result.message)) {
				report.append(" (").append(result.message).append(')');
			}
			report.append('\n');
		}

		report.append('\n').append("## Legal URL Evidence\n");
		report.append("- Términos y Condiciones URL: ").append(termsEvidence.finalUrl).append('\n');
		report.append("- Política de Privacidad URL: ").append(privacyEvidence.finalUrl).append('\n');

		report.append('\n').append("## Screenshot Evidence\n");
		report.append("- 01-dashboard-loaded.png\n");
		report.append("- 02-mi-negocio-menu-expanded.png\n");
		report.append("- 03-agregar-negocio-modal.png\n");
		report.append("- 04-administrar-negocios-view.png\n");
		report.append("- 08-terminos-y-condiciones.png\n");
		report.append("- 09-politica-de-privacidad.png\n");
		report.append("- failure-*.png (only if a step fails)\n");

		Files.writeString(ARTIFACTS_DIR.resolve("final-report.md"), report.toString(), StandardCharsets.UTF_8);
	}

	private Locator findFirstVisible(final Page page, final Locator... candidates) {
		for (final Locator candidate : candidates) {
			try {
				if (candidate != null && candidate.count() > 0 && candidate.first().isVisible()) {
					return candidate.first();
				}
			} catch (final PlaywrightException ignored) {
				// Try next candidate locator.
			}
		}

		for (final Locator candidate : candidates) {
			try {
				if (candidate != null && candidate.count() > 0) {
					candidate.first().waitFor(new Locator.WaitForOptions().setTimeout(UI_TIMEOUT_MS));
					return candidate.first();
				}
			} catch (final PlaywrightException ignored) {
				// Try next candidate locator.
			}
		}

		throw new IllegalStateException("No candidate locator is visible: " + Arrays.toString(candidates));
	}

	private Locator roleButtonByName(final Page page, final String nameRegex) {
		return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(compilePattern(nameRegex)));
	}

	private Locator roleLinkByName(final Page page, final String nameRegex) {
		return page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(compilePattern(nameRegex)));
	}

	private Locator headingByName(final Page page, final String nameRegex) {
		return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(compilePattern(nameRegex)));
	}

	private Locator textByPattern(final Page page, final String pattern) {
		return page.getByText(compilePattern(pattern));
	}

	private Pattern compilePattern(final String regex) {
		return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
	}

	private void clickAndWaitForUi(final Page page, final Locator locator) {
		locator.scrollIntoViewIfNeeded();
		locator.click();
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(UI_TIMEOUT_MS));
		} catch (final PlaywrightException ignored) {
			// SPAs with long-lived connections may never become network-idle.
		}

		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(UI_TIMEOUT_MS));
		} catch (final PlaywrightException ignored) {
			// Ignore and proceed with visible-element checks.
		}
		page.waitForTimeout(500);
	}

	private void clickIfVisible(final Page page, final Locator locator) {
		try {
			if (locator.count() > 0 && locator.first().isVisible()) {
				clickAndWaitForUi(page, locator.first());
			}
		} catch (final PlaywrightException ignored) {
			// Optional click target was not actionable.
		}
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator.count() > 0 && locator.first().isVisible();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void assertVisible(final Locator locator, final String message) {
		locator.first().waitFor(new Locator.WaitForOptions().setTimeout(UI_TIMEOUT_MS));
		assertTrue(message, locator.first().isVisible());
	}

	private void assertNotVisible(final Locator locator, final String message) {
		final long deadline = System.currentTimeMillis() + 5_000;
		while (System.currentTimeMillis() < deadline) {
			if (!isVisible(locator)) {
				return;
			}
			sleep(200);
		}
		assertTrue(message, !isVisible(locator));
	}

	private void takeScreenshot(final Page page, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(ARTIFACTS_DIR.resolve(fileName)).setFullPage(fullPage));
	}

	private boolean containsLikelyUserName(final String bodyText) {
		final Set<String> blockedPhrases = Set.of("información general", "detalles de la cuenta", "tus negocios", "sección legal",
				"business plan", "cambiar plan", "cuenta creada", "estado activo", "idioma seleccionado", "agregar negocio",
				"administrar negocios", "términos y condiciones", "política de privacidad", "mi negocio");

		final Pattern likelyName = Pattern.compile("(?U)^[\\p{L}][\\p{L}'-]{1,}(?:\\s+[\\p{L}][\\p{L}'-]{1,})+$");
		final String[] lines = bodyText.split("\\R");

		for (final String rawLine : lines) {
			final String line = rawLine.trim().replaceAll("\\s+", " ");
			final String normalized = line.toLowerCase(Locale.ROOT);

			if (line.length() < 4 || line.length() > 80 || normalized.contains("@") || normalized.matches(".*\\d.*")) {
				continue;
			}
			if (blockedPhrases.contains(normalized)) {
				continue;
			}
			if (likelyName.matcher(line).matches()) {
				return true;
			}
		}

		return false;
	}

	private boolean containsLikelyBusinessListContent(final String bodyText) {
		final String normalized = bodyText.toLowerCase(Locale.ROOT);
		return normalized.contains("tus negocios") && normalized.contains("agregar negocio")
				&& normalized.contains("tienes 2 de 3 negocios") && normalized.length() > 300;
	}

	private String resolveLoginUrl() {
		final List<String> candidates = Arrays.asList(System.getProperty("saleads.loginUrl"), System.getenv("SALEADS_LOGIN_URL"),
				System.getProperty("saleads.baseUrl"), System.getenv("SALEADS_BASE_URL"));

		final Optional<String> match = candidates.stream().filter(value -> value != null && !value.isBlank()).findFirst();
		return match.orElse(null);
	}

	private boolean isE2eEnabled() {
		final String enabledProperty = System.getProperty("saleads.e2e.enabled");
		final String enabledEnv = System.getenv("SALEADS_E2E_ENABLED");
		final String enabledValue = enabledProperty != null ? enabledProperty : enabledEnv;
		return Boolean.parseBoolean(enabledValue);
	}

	private Page waitForNewPage(final BrowserContext context, final int initialPageCount, final long timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			final List<Page> pages = context.pages();
			if (pages.size() > initialPageCount) {
				return pages.get(pages.size() - 1);
			}
			sleep(200);
		}
		return null;
	}

	private void waitForClosureOrTimeout(final Page page, final long timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			try {
				if (page.isClosed()) {
					return;
				}
			} catch (final PlaywrightException ignored) {
				return;
			}
			sleep(250);
		}
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for UI operation.", interrupted);
		}
	}

	private String slugify(final String input) {
		return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static class LegalValidationEvidence {
		private String finalUrl = "N/A";
	}

	private static class StepResult {
		private final boolean passed;
		private final String message;

		private StepResult(final boolean passed, final String message) {
			this.passed = passed;
			this.message = message;
		}

		private static StepResult pass(final String message) {
			return new StepResult(true, message);
		}

		private static StepResult fail(final String message) {
			return new StepResult(false, message == null ? "FAILED" : message);
		}
	}
}
