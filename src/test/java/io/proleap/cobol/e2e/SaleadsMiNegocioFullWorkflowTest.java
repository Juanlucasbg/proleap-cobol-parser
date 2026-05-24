package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	@Test
	public void saleadsMiNegocioFullTest() {
		final Map<String, StepResult> report = new LinkedHashMap<>();
		final Path evidenceDir = prepareEvidenceDirectory();

		try (Playwright playwright = Playwright.create()) {
			final boolean headless = Boolean.parseBoolean(valueOrDefault(System.getProperty("saleads.headless"),
					System.getenv("SALEADS_HEADLESS"), "true"));
			final double slowMoMs = parseDouble(valueOrDefault(System.getProperty("saleads.slowMoMs"),
					System.getenv("SALEADS_SLOWMO_MS"), "250"));

			try (Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(slowMoMs));
					BrowserContext context = browser
							.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000))) {
				context.setDefaultTimeout(20_000);
				final Page page = context.newPage();

				openLoginPage(page);

				runStep(report, "Login", () -> {
					loginWithGoogle(page);
					assertVisible(findByVisibleText(page, "Negocio"), "Left sidebar navigation should be visible.");
					captureScreenshot(page, evidenceDir, "01-dashboard-loaded", false);
				});

				runStep(report, "Mi Negocio menu", () -> {
					clickByVisibleTextAndWait(page, "Mi Negocio");
					assertVisible(findByVisibleText(page, "Agregar Negocio"), "'Agregar Negocio' should be visible.");
					assertVisible(findByVisibleText(page, "Administrar Negocios"),
							"'Administrar Negocios' should be visible.");
					captureScreenshot(page, evidenceDir, "02-mi-negocio-expanded", false);
				});

				runStep(report, "Agregar Negocio modal", () -> {
					clickByVisibleTextAndWait(page, "Agregar Negocio");
					assertVisible(findByVisibleText(page, "Crear Nuevo Negocio"), "Modal title should be visible.");
					assertVisible(findByVisibleText(page, "Nombre del Negocio"),
							"'Nombre del Negocio' field label should be visible.");
					assertVisible(findByVisibleText(page, "Tienes 2 de 3 negocios"),
							"'Tienes 2 de 3 negocios' should be visible.");
					assertVisible(findByVisibleText(page, "Cancelar"), "'Cancelar' button should be visible.");
					assertVisible(findByVisibleText(page, "Crear Negocio"), "'Crear Negocio' button should be visible.");
					captureScreenshot(page, evidenceDir, "03-agregar-negocio-modal", false);

					fillBusinessNameAndCancel(page);
				});

				runStep(report, "Administrar Negocios view", () -> {
					final Locator administrarNegociosLink = findByVisibleText(page, "Administrar Negocios");
					if (administrarNegociosLink.count() == 0 || !administrarNegociosLink.first().isVisible()) {
						clickByVisibleTextAndWait(page, "Mi Negocio");
					}

					clickByVisibleTextAndWait(page, "Administrar Negocios");
					assertVisible(findByVisibleText(page, "Información General"), "'Información General' should be visible.");
					assertVisible(findByVisibleText(page, "Detalles de la Cuenta"),
							"'Detalles de la Cuenta' should be visible.");
					assertVisible(findByVisibleText(page, "Tus Negocios"), "'Tus Negocios' should be visible.");
					assertVisible(findByVisibleText(page, "Sección Legal"), "'Sección Legal' should be visible.");
					captureScreenshot(page, evidenceDir, "04-administrar-negocios-full", true);
				});

				runStep(report, "Información General", () -> {
					assertVisible(findByVisibleText(page, "BUSINESS PLAN"), "'BUSINESS PLAN' should be visible.");
					assertVisible(findByVisibleText(page, "Cambiar Plan"), "'Cambiar Plan' button should be visible.");

					final String visibleText = page.innerText("body");
					assertTrue("User email should be visible in account page.", EMAIL_PATTERN.matcher(visibleText).find());
					assertTrue("User name should be visible in account page.", hasLikelyUserName(visibleText));
				});

				runStep(report, "Detalles de la Cuenta", () -> {
					assertVisible(findByVisibleText(page, "Cuenta creada"), "'Cuenta creada' should be visible.");
					assertVisible(findByVisibleText(page, "Estado activo"), "'Estado activo' should be visible.");
					assertVisible(findByVisibleText(page, "Idioma seleccionado"),
							"'Idioma seleccionado' should be visible.");
				});

				runStep(report, "Tus Negocios", () -> {
					assertVisible(findByVisibleText(page, "Tus Negocios"), "Business list section should be visible.");
					assertVisible(findByVisibleText(page, "Agregar Negocio"), "'Agregar Negocio' should be visible.");
					assertVisible(findByVisibleText(page, "Tienes 2 de 3 negocios"),
							"'Tienes 2 de 3 negocios' should be visible.");
				});

				runStep(report, "Términos y Condiciones", () -> {
					final LegalNavigationResult legalResult = openLegalContent(context, page, "Términos y Condiciones");
					assertVisible(findByVisibleText(legalResult.targetPage, "Términos y Condiciones"),
							"Heading 'Términos y Condiciones' should be visible.");
					assertTrue("Legal content text should be visible for Términos y Condiciones.",
							legalResult.targetPage.innerText("body").trim().length() > 80);
					captureScreenshot(legalResult.targetPage, evidenceDir, "05-terminos-y-condiciones", true);
					System.out.println("FINAL_URL_TERMINOS_Y_CONDICIONES=" + legalResult.targetPage.url());
					legalResult.cleanup.run();
				});

				runStep(report, "Política de Privacidad", () -> {
					final LegalNavigationResult legalResult = openLegalContent(context, page, "Política de Privacidad");
					assertVisible(findByVisibleText(legalResult.targetPage, "Política de Privacidad"),
							"Heading 'Política de Privacidad' should be visible.");
					assertTrue("Legal content text should be visible for Política de Privacidad.",
							legalResult.targetPage.innerText("body").trim().length() > 80);
					captureScreenshot(legalResult.targetPage, evidenceDir, "06-politica-de-privacidad", true);
					System.out.println("FINAL_URL_POLITICA_DE_PRIVACIDAD=" + legalResult.targetPage.url());
					legalResult.cleanup.run();
				});

				printFinalReport(report, evidenceDir);

				final List<String> failedSteps = new ArrayList<>();
				for (Map.Entry<String, StepResult> entry : report.entrySet()) {
					if (!entry.getValue().passed) {
						failedSteps.add(entry.getKey());
					}
				}
				assertTrue("One or more workflow steps failed: " + failedSteps, failedSteps.isEmpty());
			}
		}
	}

	private void openLoginPage(final Page page) {
		final String startUrl = valueOrDefault(System.getProperty("saleads.startUrl"), System.getenv("SALEADS_START_URL"),
				System.getenv("SALEADS_BASE_URL"));

		if (startUrl == null) {
			fail("Provide SALEADS_START_URL or -Dsaleads.startUrl with the login page URL for the current environment.");
		}

		page.navigate(startUrl);
		waitForUi(page);
	}

	private void loginWithGoogle(final Page page) {
		final Locator loginButton = firstExistingLocator(page, "Sign in with Google", "Iniciar sesión con Google",
				"Continuar con Google", "Ingresar con Google", "Acceder con Google", "Google");
		assertTrue("Google login button should exist.", loginButton.count() > 0);

		Page popup = null;
		try {
			popup = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(8_000),
					() -> loginButton.first().click());
		} catch (PlaywrightException ignored) {
			loginButton.first().click();
		}

		waitForUi(page);

		if (popup != null) {
			waitForUi(popup);
			selectGoogleAccountIfPresent(popup);
		} else {
			selectGoogleAccountIfPresent(page);
		}

		waitForUi(page);
	}

	private void selectGoogleAccountIfPresent(final Page activePage) {
		final Locator accountOption = activePage
				.getByText(Pattern.compile("(?i).*" + Pattern.quote(GOOGLE_ACCOUNT_EMAIL) + ".*"));

		if (accountOption.count() > 0) {
			accountOption.first().click();
			waitForUi(activePage);
		}
	}

	private void fillBusinessNameAndCancel(final Page page) {
		Locator businessNameField = page.getByLabel("Nombre del Negocio");
		if (businessNameField.count() == 0) {
			businessNameField = page.getByPlaceholder("Nombre del Negocio");
		}
		if (businessNameField.count() == 0) {
			businessNameField = page.locator("input").filter(new Locator.FilterOptions().setHasText("Nombre del Negocio"));
		}

		if (businessNameField.count() > 0) {
			businessNameField.first().click();
			businessNameField.first().fill("Negocio Prueba Automatización");
			waitForUi(page);
		}

		clickByVisibleTextAndWait(page, "Cancelar");
	}

	private LegalNavigationResult openLegalContent(final BrowserContext context, final Page appPage, final String linkText) {
		Page legalPage = null;
		try {
			legalPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(8_000),
					() -> clickByVisibleTextAndWait(appPage, linkText));
		} catch (PlaywrightException ignored) {
			clickByVisibleTextAndWait(appPage, linkText);
		}

		if (legalPage != null) {
			waitForUi(legalPage);
			final Page openedLegalPage = legalPage;
			return new LegalNavigationResult(openedLegalPage, () -> openedLegalPage.close());
		}

		waitForUi(appPage);
		return new LegalNavigationResult(appPage, () -> {
			appPage.goBack();
			waitForUi(appPage);
		});
	}

	private Locator firstExistingLocator(final Page page, final String... texts) {
		for (final String text : texts) {
			final Locator locator = findByVisibleText(page, text);
			if (locator.count() > 0) {
				return locator;
			}
		}
		return page.locator("__no_match__");
	}

	private void clickByVisibleTextAndWait(final Page page, final String text) {
		final Locator target = findByVisibleText(page, text);
		assertTrue("Expected clickable element with text '" + text + "'.", target.count() > 0);
		target.first().scrollIntoViewIfNeeded();
		target.first().click();
		waitForUi(page);
	}

	private Locator findByVisibleText(final Page page, final String text) {
		final Pattern pattern = Pattern.compile("(?i).*" + Pattern.quote(text) + ".*");
		final Locator button = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern));
		if (button.count() > 0) {
			return button;
		}
		final Locator link = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern));
		if (link.count() > 0) {
			return link;
		}
		final Locator menuItem = page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(pattern));
		if (menuItem.count() > 0) {
			return menuItem;
		}
		return page.getByText(pattern);
	}

	private void assertVisible(final Locator locator, final String message) {
		assertTrue(message, locator.count() > 0 && locator.first().isVisible());
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(6_000));
		} catch (PlaywrightException ignored) {
			// Some SPA routes do not reach network idle, so we continue with a short stability wait.
		}
		page.waitForTimeout(800);
	}

	private Path captureScreenshot(final Page page, final Path evidenceDir, final String checkpointName,
			final boolean fullPage) {
		final String filename = sanitize(checkpointName) + "-" + TS_FORMAT.format(LocalDateTime.now()) + ".png";
		final Path destination = evidenceDir.resolve(filename);
		page.screenshot(new Page.ScreenshotOptions().setPath(destination).setFullPage(fullPage));
		System.out.println("SCREENSHOT=" + destination);
		return destination;
	}

	private Path prepareEvidenceDirectory() {
		final Path evidenceDir = Paths.get("target", "saleads-evidence", TS_FORMAT.format(LocalDateTime.now()));
		try {
			Files.createDirectories(evidenceDir);
		} catch (IOException e) {
			throw new IllegalStateException("Could not create evidence directory: " + evidenceDir, e);
		}
		return evidenceDir;
	}

	private void runStep(final Map<String, StepResult> report, final String stepName, final ThrowingRunnable action) {
		try {
			action.run();
			report.put(stepName, StepResult.pass());
		} catch (Throwable t) {
			report.put(stepName, StepResult.fail(cleanMessage(t)));
		}
	}

	private void printFinalReport(final Map<String, StepResult> report, final Path evidenceDir) {
		System.out.println("=== FINAL REPORT: saleads_mi_negocio_full_test ===");
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			System.out.printf("%s: %s%n", entry.getKey(), entry.getValue().passed ? "PASS" : "FAIL");
			if (!entry.getValue().passed) {
				System.out.println("  Reason: " + entry.getValue().details);
			}
		}
		System.out.println("EVIDENCE_DIR=" + evidenceDir.toAbsolutePath());
	}

	private boolean hasLikelyUserName(final String pageText) {
		final Matcher emailMatcher = EMAIL_PATTERN.matcher(pageText);
		final String textWithoutEmail = emailMatcher.replaceAll(" ");
		final Pattern namePattern = Pattern.compile("\\b[A-ZÁÉÍÓÚÑ][a-záéíóúñ]{2,}\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]{2,}\\b");
		return namePattern.matcher(textWithoutEmail).find();
	}

	private String cleanMessage(final Throwable t) {
		if (t == null || t.getMessage() == null || t.getMessage().isBlank()) {
			return "No error message available.";
		}
		return t.getMessage().replaceAll("\\s+", " ").trim();
	}

	private String sanitize(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("-{2,}", "-").replaceAll("(^-|-$)", "");
	}

	private String valueOrDefault(final String first, final String second, final String fallback) {
		if (first != null && !first.isBlank()) {
			return first;
		}
		if (second != null && !second.isBlank()) {
			return second;
		}
		return fallback;
	}

	private double parseDouble(final String rawValue) {
		try {
			return Double.parseDouble(rawValue);
		} catch (NumberFormatException e) {
			return 250d;
		}
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run();
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass() {
			return new StepResult(true, "");
		}

		private static StepResult fail(final String reason) {
			return new StepResult(false, reason);
		}
	}

	private static final class LegalNavigationResult {
		private final Page targetPage;
		private final Runnable cleanup;

		private LegalNavigationResult(final Page targetPage, final Runnable cleanup) {
			this.targetPage = targetPage;
			this.cleanup = cleanup;
		}
	}
}
