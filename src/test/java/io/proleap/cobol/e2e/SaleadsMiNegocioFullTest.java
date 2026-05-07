package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioFullTest {

	private static final int TIMEOUT_MS = 20000;
	private static final String GOOGLE_TEST_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter RUN_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String entryUrl = firstNonBlank(System.getProperty("saleads.entry.url"), System.getenv("SALEADS_ENTRY_URL"));
		Assume.assumeTrue(
				"Set saleads.entry.url system property or SALEADS_ENTRY_URL environment variable to run this E2E test.",
				entryUrl != null && !entryUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(
				firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"), "true"));
		final Path artifactsDir = createArtifactsDirectory();
		final Map<String, StepResult> stepResults = initializeStepResults();
		final Map<String, String> evidence = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(150));
			final BrowserContext context = browser.newContext(
					new Browser.NewContextOptions().setViewportSize(1600, 1000));
			final Page page = context.newPage();

			page.navigate(entryUrl);
			safeWaitForUi(page);

			runStep("Login", stepResults, () -> {
				loginWithGoogle(page, context);
				waitForAnyVisible("Main app interface", page,
						page.getByRole(AriaRole.NAVIGATION),
						page.getByText(regexContains("Mi Negocio")),
						page.getByText(regexContains("Negocio")));
				takeScreenshot(page, artifactsDir.resolve("01-dashboard.png"), false);
			});

			runStep("Mi Negocio menu", stepResults, () -> {
				openMiNegocioMenu(page);
				assertVisible("Agregar Negocio must be visible", page.getByText(regexContains("Agregar Negocio")));
				assertVisible("Administrar Negocios must be visible",
						page.getByText(regexContains("Administrar Negocios")));
				takeScreenshot(page, artifactsDir.resolve("02-mi-negocio-menu-expanded.png"), false);
			});

			runStep("Agregar Negocio modal", stepResults, () -> {
				clickByVisibleText(page, "Agregar Negocio");
				assertVisible("Crear Nuevo Negocio modal title must be visible",
						page.getByText(regexContains("Crear Nuevo Negocio")));
				assertVisible("Nombre del Negocio input must exist",
						page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName(regexContains("Nombre del Negocio"))));
				assertVisible("Tienes 2 de 3 negocios text must be visible",
						page.getByText(regexContains("Tienes 2 de 3 negocios")));
				assertVisible("Cancelar button must be present",
						page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(regexContains("Cancelar"))));
				assertVisible("Crear Negocio button must be present",
						page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(regexContains("Crear Negocio"))));

				Locator businessName = page.getByRole(AriaRole.TEXTBOX,
						new Page.GetByRoleOptions().setName(regexContains("Nombre del Negocio"))).first();
				businessName.click();
				safeWaitForUi(page);
				businessName.fill("Negocio Prueba Automatizacion");
				takeScreenshot(page, artifactsDir.resolve("03-agregar-negocio-modal.png"), false);

				clickByVisibleText(page, "Cancelar");
			});

			runStep("Administrar Negocios view", stepResults, () -> {
				openMiNegocioMenu(page);
				clickByVisibleText(page, "Administrar Negocios");
				assertVisible("Información General section must exist",
						page.getByText(regexContains("Información General")));
				assertVisible("Detalles de la Cuenta section must exist",
						page.getByText(regexContains("Detalles de la Cuenta")));
				assertVisible("Tus Negocios section must exist",
						page.getByText(regexContains("Tus Negocios")));
				assertVisible("Sección Legal section must exist",
						page.getByText(regexContains("Sección Legal")));
				takeScreenshot(page, artifactsDir.resolve("04-administrar-negocios-view.png"), true);
			});

			runStep("Información General", stepResults, () -> {
				assertVisible("Información General heading must be visible",
						page.getByText(regexContains("Información General")));
				assertVisible("BUSINESS PLAN text must be visible",
						page.getByText(regexContains("BUSINESS PLAN")));
				assertVisible("Cambiar Plan button must be visible",
						page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(regexContains("Cambiar Plan"))));
				assertTrue("User email must be visible",
						contains(page.locator("body").innerText(),
								Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")));
				assertTrue("User name-like text must be visible",
						contains(page.locator("body").innerText(),
								Pattern.compile("(?m)^[\\p{L}][\\p{L} .'-]{2,}$")));
			});

			runStep("Detalles de la Cuenta", stepResults, () -> {
				assertVisible("'Cuenta creada' text must be visible",
						page.getByText(regexContains("Cuenta creada")));
				assertVisible("'Estado activo' text must be visible",
						page.getByText(regexContains("Estado activo")));
				assertVisible("'Idioma seleccionado' text must be visible",
						page.getByText(regexContains("Idioma seleccionado")));
			});

			runStep("Tus Negocios", stepResults, () -> {
				assertVisible("Tus Negocios section must be visible",
						page.getByText(regexContains("Tus Negocios")));
				assertVisible("Agregar Negocio button must exist",
						page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(regexContains("Agregar Negocio"))));
				assertVisible("'Tienes 2 de 3 negocios' text must be visible",
						page.getByText(regexContains("Tienes 2 de 3 negocios")));

				Locator businessesContainer = page.getByText(regexContains("Tus Negocios")).first()
						.locator("xpath=ancestor::*[self::section or self::div][1]");
				assertTrue("Business list content must be visible",
						businessesContainer.innerText().replaceAll("\\s+", " ").trim().length() > 15);
			});

			runStep("Términos y Condiciones", stepResults, () -> {
				final String termsUrl = validateLegalLink(page, context, artifactsDir,
						"Términos y Condiciones",
						"08-terminos-y-condiciones.png");
				evidence.put("Términos y Condiciones URL", termsUrl);
			});

			runStep("Política de Privacidad", stepResults, () -> {
				final String privacyUrl = validateLegalLink(page, context, artifactsDir,
						"Política de Privacidad",
						"09-politica-de-privacidad.png");
				evidence.put("Política de Privacidad URL", privacyUrl);
			});

			writeFinalReport(stepResults, evidence, artifactsDir.resolve("10-final-report.txt"));
			assertAllStepsPassed(stepResults);
		}
	}

	private String validateLegalLink(final Page appPage, final BrowserContext context, final Path artifactsDir,
			final String legalLinkText, final String screenshotName) {
		openSectionIfNeeded(appPage, "Sección Legal");
		final String appUrlBeforeClick = appPage.url();
		final int pagesBefore = context.pages().size();
		final Locator legalLink = findByVisibleText(appPage, legalLinkText);
		legalLink.click();
		safeWaitForUi(appPage);

		Page legalPage = waitForNewTabIfAny(context, pagesBefore, appPage);
		if (legalPage != appPage) {
			legalPage.bringToFront();
			safeWaitForUi(legalPage);
		}

		waitForAnyVisible("Legal heading must be visible on legal page", legalPage,
				legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(regexContains(legalLinkText))),
				legalPage.getByText(regexContains(legalLinkText)));
		String legalBody = legalPage.locator("body").innerText();
		assertTrue("Legal content text must be visible", legalBody != null && legalBody.trim().length() > 120);
		takeScreenshot(legalPage, artifactsDir.resolve(screenshotName), true);
		final String finalUrl = legalPage.url();

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			safeWaitForUi(appPage);
		} else if (!appPage.url().equals(appUrlBeforeClick)) {
			appPage.goBack();
			safeWaitForUi(appPage);
		}

		return finalUrl;
	}

	private void loginWithGoogle(final Page appPage, final BrowserContext context) {
		final int pagesBefore = context.pages().size();
		final Locator loginButton = firstVisible(
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*(google).*"))),
				appPage.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*(google).*"))),
				appPage.getByText(Pattern.compile("(?iu)sign in with google|iniciar sesi[oó]n con google|google")));
		assertTrue("Login/Sign in with Google button must exist", loginButton != null);

		loginButton.click();
		safeWaitForUi(appPage);

		Page authPage = waitForNewTabIfAny(context, pagesBefore, appPage);
		final Locator accountOption = authPage.getByText(Pattern.compile(Pattern.quote(GOOGLE_TEST_ACCOUNT)));
		if (isVisible(accountOption)) {
			accountOption.click();
			safeWaitForUi(authPage);
		}

		if (authPage != appPage) {
			waitForSidebar(appPage);
			appPage.bringToFront();
		}

		waitForSidebar(appPage);
	}

	private void waitForSidebar(final Page page) {
		waitForAnyVisible("Left sidebar navigation should be visible", page,
				page.getByRole(AriaRole.NAVIGATION),
				page.getByText(regexContains("Negocio")),
				page.getByText(regexContains("Mi Negocio")));
	}

	private void openMiNegocioMenu(final Page page) {
		openSectionIfNeeded(page, "Negocio");
		Locator agregarNegocio = page.getByText(regexContains("Agregar Negocio"));
		if (!isVisible(agregarNegocio)) {
			clickByVisibleText(page, "Mi Negocio");
		}
	}

	private void openSectionIfNeeded(final Page page, final String sectionName) {
		if (isVisible(page.getByText(regexContains(sectionName)))) {
			return;
		}
		Locator section = firstVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(regexContains(sectionName))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(regexContains(sectionName))),
				page.getByText(regexContains(sectionName)));
		if (section != null) {
			section.click();
			safeWaitForUi(page);
		}
	}

	private void clickByVisibleText(final Page page, final String text) {
		Locator target = findByVisibleText(page, text);
		target.click();
		safeWaitForUi(page);
	}

	private Locator findByVisibleText(final Page page, final String text) {
		Locator target = firstVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(regexContains(text))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(regexContains(text))),
				page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(regexContains(text))),
				page.getByText(regexContains(text)));
		if (target == null) {
			throw new AssertionError("Could not find visible element with text: " + text);
		}
		return target;
	}

	private Page waitForNewTabIfAny(final BrowserContext context, final int pagesBefore, final Page defaultPage) {
		for (int i = 0; i < 20; i++) {
			List<Page> pages = context.pages();
			if (pages.size() > pagesBefore) {
				return pages.get(pages.size() - 1);
			}
			defaultPage.waitForTimeout(250);
		}
		return defaultPage;
	}

	private void assertVisible(final String message, final Locator locator) {
		locator.first().waitFor(new Locator.WaitForOptions().setTimeout(TIMEOUT_MS).setState(WaitForSelectorState.VISIBLE));
		assertTrue(message, locator.first().isVisible());
	}

	private void waitForAnyVisible(final String message, final Page page, final Locator... options) {
		for (int i = 0; i < 20; i++) {
			for (Locator option : options) {
				if (isVisible(option)) {
					return;
				}
			}
			page.waitForTimeout(500);
		}
		throw new AssertionError(message);
	}

	private Locator firstVisible(final Locator... options) {
		for (Locator option : options) {
			if (isVisible(option)) {
				return option.first();
			}
		}
		return null;
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator != null && locator.count() > 0 && locator.first().isVisible();
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private Pattern regexContains(final String text) {
		StringBuilder pattern = new StringBuilder("(?iu).*");
		for (char character : text.toCharArray()) {
			switch (Character.toLowerCase(character)) {
			case 'a':
			case 'á':
				pattern.append("[aá]");
				break;
			case 'e':
			case 'é':
				pattern.append("[eé]");
				break;
			case 'i':
			case 'í':
				pattern.append("[ií]");
				break;
			case 'o':
			case 'ó':
				pattern.append("[oó]");
				break;
			case 'u':
			case 'ú':
				pattern.append("[uú]");
				break;
			case 'n':
			case 'ñ':
				pattern.append("[nñ]");
				break;
			default:
				pattern.append(Pattern.quote(Character.toString(character)));
				break;
			}
		}
		pattern.append(".*");
		return Pattern.compile(pattern.toString());
	}

	private void safeWaitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (PlaywrightException ignored) {
			try {
				page.waitForLoadState(LoadState.DOMCONTENTLOADED);
			} catch (PlaywrightException ignoredAgain) {
				// keep flow resilient across environments with dynamic loading.
			}
		}
		page.waitForTimeout(500);
	}

	private void takeScreenshot(final Page page, final Path path, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private Map<String, StepResult> initializeStepResults() {
		Map<String, StepResult> results = new LinkedHashMap<>();
		results.put("Login", StepResult.notRun());
		results.put("Mi Negocio menu", StepResult.notRun());
		results.put("Agregar Negocio modal", StepResult.notRun());
		results.put("Administrar Negocios view", StepResult.notRun());
		results.put("Información General", StepResult.notRun());
		results.put("Detalles de la Cuenta", StepResult.notRun());
		results.put("Tus Negocios", StepResult.notRun());
		results.put("Términos y Condiciones", StepResult.notRun());
		results.put("Política de Privacidad", StepResult.notRun());
		return results;
	}

	private void runStep(final String stepName, final Map<String, StepResult> stepResults, final CheckedRunnable body) {
		try {
			body.run();
			stepResults.put(stepName, StepResult.pass());
		} catch (Throwable throwable) {
			stepResults.put(stepName, StepResult.fail(throwable.getMessage()));
		}
	}

	private void assertAllStepsPassed(final Map<String, StepResult> stepResults) {
		List<String> failedSteps = new ArrayList<>();
		for (Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			if (!"PASS".equals(entry.getValue().status)) {
				failedSteps.add(entry.getKey() + " -> " + entry.getValue().status + ": " + entry.getValue().details);
			}
		}
		if (!failedSteps.isEmpty()) {
			fail("saleads_mi_negocio_full_test failed:\n" + String.join("\n", failedSteps));
		}
	}

	private void writeFinalReport(final Map<String, StepResult> stepResults, final Map<String, String> evidence, final Path outputFile)
			throws IOException {
		List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test");
		lines.add("=================================");
		for (Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			lines.add(entry.getKey() + ": " + entry.getValue().status
					+ (entry.getValue().details == null ? "" : " (" + entry.getValue().details + ")"));
		}
		if (!evidence.isEmpty()) {
			lines.add("");
			lines.add("Evidence");
			lines.add("--------");
			for (Map.Entry<String, String> entry : evidence.entrySet()) {
				lines.add(entry.getKey() + ": " + entry.getValue());
			}
		}

		Files.write(outputFile, lines, StandardCharsets.UTF_8);
	}

	private Path createArtifactsDirectory() throws IOException {
		Path folder = Paths.get("target", "e2e-artifacts", "saleads_mi_negocio_full_test",
				LocalDateTime.now().format(RUN_ID_FORMAT));
		Files.createDirectories(folder);
		return folder;
	}

	private boolean contains(final String text, final Pattern pattern) {
		return text != null && pattern.matcher(text).find();
	}

	private String firstNonBlank(final String... candidates) {
		for (String candidate : candidates) {
			if (candidate != null && !candidate.isBlank()) {
				return candidate;
			}
		}
		return null;
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static class StepResult {
		private final String status;
		private final String details;

		private StepResult(final String status, final String details) {
			this.status = status;
			this.details = details;
		}

		private static StepResult pass() {
			return new StepResult("PASS", null);
		}

		private static StepResult fail(final String details) {
			return new StepResult("FAIL", details == null ? "No error details available." : details);
		}

		private static StepResult notRun() {
			return new StepResult("NOT_RUN", "Step did not execute.");
		}
	}
}
