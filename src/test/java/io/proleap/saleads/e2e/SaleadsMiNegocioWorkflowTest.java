package io.proleap.saleads.e2e;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
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

/**
 * End-to-end workflow validation for the SaleADS "Mi Negocio" module.
 *
 * <p>This test intentionally avoids environment-specific URLs and selectors by:
 * <ul>
 *   <li>requiring the login URL from configuration (property/env)</li>
 *   <li>finding UI elements mostly by visible text and roles</li>
 *   <li>handling legal links whether they open in the same tab or a new tab</li>
 * </ul>
 *
 * <p>Execution requirements:
 * <ul>
 *   <li>JVM property {@code -Dsaleads.login.url=} or env {@code SALEADS_LOGIN_URL}</li>
 *   <li>Google account selector should include {@code juanlucasbarbiergarzon@gmail.com}</li>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final long DEFAULT_TIMEOUT_MS = 30_000L;
	private static final int CASE_INSENSITIVE_FLAGS = Pattern.CASE_INSENSITIVE;

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page page;
	private Path evidenceDir;
	private final Map<String, StepOutcome> stepReport = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDir = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);

		playwright = Playwright.create();
		final boolean headless = Boolean
				.parseBoolean(System.getProperty("saleads.headless", System.getenv().getOrDefault("SALEADS_HEADLESS", "true")));
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
		page = context.newPage();
		page.navigate(resolveLoginUrl());
		waitForUiLoad(page);
	}

	@After
	public void tearDown() {
		writeFinalReport();

		if (context != null) {
			context.close();
		}

		if (browser != null) {
			browser.close();
		}

		if (playwright != null) {
			playwright.close();
		}
	}

	@Test
	public void shouldValidateSaleadsMiNegocioFlow() {
		runStep("Login", () -> {
			loginWithGoogle();
			captureScreenshot("01-dashboard-loaded", false, page);
			return null;
		});

		runStep("Mi Negocio menu", () -> {
			openMiNegocioMenu();
			captureScreenshot("02-mi-negocio-menu-expanded", false, page);
			return null;
		});

		runStep("Agregar Negocio modal", () -> {
			validateAgregarNegocioModal();
			return null;
		});

		runStep("Administrar Negocios view", () -> {
			openAdministrarNegocios();
			captureScreenshot("04-administrar-negocios-view", true, page);
			return null;
		});

		runStep("Informacion General", () -> {
			validateInformacionGeneral();
			return null;
		});

		runStep("Detalles de la Cuenta", () -> {
			validateDetallesCuenta();
			return null;
		});

		runStep("Tus Negocios", () -> {
			validateTusNegocios();
			return null;
		});

		runStep("Terminos y Condiciones", () -> validateLegalDocument(
				new String[] { "Términos y Condiciones", "Terminos y Condiciones" },
				new String[] { "Términos y Condiciones", "Terminos y Condiciones" },
				"08-terminos-y-condiciones"));

		runStep("Politica de Privacidad", () -> validateLegalDocument(
				new String[] { "Política de Privacidad", "Politica de Privacidad" },
				new String[] { "Política de Privacidad", "Politica de Privacidad" },
				"09-politica-de-privacidad"));

		final List<String> failures = stepReport.entrySet().stream().filter(entry -> !entry.getValue().passed())
				.map(entry -> entry.getKey() + " -> " + entry.getValue().details()).collect(Collectors.toList());

		assertTrue("One or more SaleADS workflow validations failed:\n" + String.join("\n", failures), failures.isEmpty());
	}

	private void loginWithGoogle() {
		final Locator loginButton = waitForAnyVisibleElement(new String[] { "Sign in with Google", "Iniciar con Google",
				"Iniciar sesión con Google", "Continuar con Google", "Login with Google" });
		assertNotNull("Google login button was not found.", loginButton);

		Page googlePage = null;
		try {
			googlePage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(7_000),
					() -> clickAndWait(loginButton));
		} catch (PlaywrightException ignored) {
			clickAndWait(loginButton);
		}

		final Page authPage = googlePage != null ? googlePage : page;
		chooseGoogleAccountIfVisible(authPage);

		waitForUiLoad(page);
		assertAnyTextVisible("Main app interface did not render after login.", "Negocio");
		assertVisible(page.locator("aside, nav").first(), "Left sidebar navigation should be visible.");
	}

	private void chooseGoogleAccountIfVisible(final Page authPage) {
		waitForUiLoad(authPage);

		final Locator accountOption = authPage.getByText(caseInsensitiveLiteralPattern(GOOGLE_ACCOUNT_EMAIL)).first();
		if (accountOption.isVisible()) {
			accountOption.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
			waitForUiLoad(authPage);
			waitForUiLoad(page);
		}

		if (authPage != page) {
			authPage.bringToFront();
			waitForUiLoad(authPage);
			page.bringToFront();
		}
	}

	private void openMiNegocioMenu() {
		clickAndWait(waitForAnyVisibleElement(new String[] { "Negocio" }));
		clickAndWait(waitForAnyVisibleElement(new String[] { "Mi Negocio" }));

		assertAnyTextVisible("Mi Negocio submenu did not expand (Agregar Negocio missing).", "Agregar Negocio");
		assertAnyTextVisible("Mi Negocio submenu did not expand (Administrar Negocios missing).", "Administrar Negocios");
	}

	private void validateAgregarNegocioModal() {
		clickAndWait(waitForAnyVisibleElement(new String[] { "Agregar Negocio" }));

		assertAnyTextVisible("Expected modal title not visible.", "Crear Nuevo Negocio");
		assertInputPresent("Nombre del Negocio");
		assertAnyTextVisible("Business limit text not found.", "Tienes 2 de 3 negocios");
		assertAnyTextVisible("Cancelar button is missing.", "Cancelar");
		assertAnyTextVisible("Crear Negocio button is missing.", "Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal", false, page);

		final Locator nameInput = findInput("Nombre del Negocio");
		if (nameInput != null && nameInput.isVisible()) {
			nameInput.fill("Negocio Prueba Automatizacion");
			clickAndWait(waitForAnyVisibleElement(new String[] { "Cancelar" }));
		}
	}

	private void openAdministrarNegocios() {
		if (!isAnyTextVisible("Administrar Negocios")) {
			clickAndWait(waitForAnyVisibleElement(new String[] { "Mi Negocio" }));
		}

		clickAndWait(waitForAnyVisibleElement(new String[] { "Administrar Negocios" }));

		assertAnyTextVisible("Missing section heading: Informacion General.", "Información General", "Informacion General");
		assertAnyTextVisible("Missing section heading: Detalles de la Cuenta.", "Detalles de la Cuenta");
		assertAnyTextVisible("Missing section heading: Tus Negocios.", "Tus Negocios");
		assertAnyTextVisible("Missing section heading: Seccion Legal.", "Sección Legal", "Seccion Legal");
	}

	private void validateInformacionGeneral() {
		assertAnyTextVisible("Plan badge BUSINESS PLAN is not visible.", "BUSINESS PLAN");
		assertAnyTextVisible("Cambiar Plan button is not visible.", "Cambiar Plan");

		final String bodyText = page.locator("body").innerText();
		final Matcher matcher = EMAIL_PATTERN.matcher(bodyText);
		assertTrue("No user email found in Informacion General.", matcher.find());
		assertTrue("No likely user name found in Informacion General.", looksLikeUserNameIsVisible(bodyText));
	}

	private void validateDetallesCuenta() {
		assertAnyTextVisible("Expected label not visible: Cuenta creada.", "Cuenta creada");
		assertPatternVisible("Expected label not visible: Estado activo.",
				Pattern.compile("Estado\\s+activo", CASE_INSENSITIVE_FLAGS));
		assertAnyTextVisible("Expected label not visible: Idioma seleccionado.", "Idioma seleccionado");
	}

	private void validateTusNegocios() {
		assertAnyTextVisible("Tus Negocios section is not visible.", "Tus Negocios");
		assertAnyTextVisible("Agregar Negocio button is missing in Tus Negocios.", "Agregar Negocio");
		assertAnyTextVisible("Business usage text is missing in Tus Negocios.", "Tienes 2 de 3 negocios");
	}

	private String validateLegalDocument(final String[] linkText, final String[] headingText, final String screenshotName) {
		final String appUrlBefore = page.url();
		final Locator legalLink = waitForAnyVisibleElement(linkText);
		assertNotNull("Legal link not found: " + Arrays.toString(linkText), legalLink);

		Page legalPage = null;
		try {
			legalPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(7_000),
					() -> clickAndWait(legalLink));
		} catch (PlaywrightException ignored) {
			clickAndWait(legalLink);
		}

		final Page targetPage = legalPage != null ? legalPage : page;
		waitForUiLoad(targetPage);
		assertAnyTextVisibleOnPage(targetPage, "Legal heading not visible for: " + Arrays.toString(headingText), headingText);

		final String legalText = targetPage.locator("body").innerText();
		assertTrue("Legal content appears empty for: " + Arrays.toString(linkText), legalText != null && legalText.trim().length() > 120);

		captureScreenshot(screenshotName, true, targetPage);
		final String legalUrl = targetPage.url();

		if (targetPage != page) {
			targetPage.close();
			page.bringToFront();
			waitForUiLoad(page);
		} else if (!appUrlBefore.equals(page.url())) {
			page.goBack(new Page.GoBackOptions().setTimeout(DEFAULT_TIMEOUT_MS));
			waitForUiLoad(page);
		}

		return legalUrl;
	}

	private void runStep(final String stepName, final StepExecutable executable) {
		try {
			final String details = executable.run();
			stepReport.put(stepName, StepOutcome.pass(details));
		} catch (Throwable ex) {
			captureFailure(stepName);
			stepReport.put(stepName, StepOutcome.fail(ex.getMessage()));
		}
	}

	private void captureFailure(final String stepName) {
		final String screenshotName = "failure-" + sanitize(stepName.toLowerCase(Locale.ROOT));
		try {
			captureScreenshot(screenshotName, true, page);
		} catch (Exception ignored) {
			// Best effort only: do not mask the original step failure.
		}
	}

	private void writeFinalReport() {
		if (evidenceDir == null) {
			return;
		}

		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("SaleADS Mi Negocio workflow report").append(System.lineSeparator());
		reportBuilder.append("Evidence directory: ").append(evidenceDir).append(System.lineSeparator()).append(System.lineSeparator());

		for (final Map.Entry<String, StepOutcome> entry : stepReport.entrySet()) {
			reportBuilder.append(entry.getKey()).append(": ").append(entry.getValue().passed() ? "PASS" : "FAIL");
			if (entry.getValue().details() != null && !entry.getValue().details().isBlank()) {
				reportBuilder.append(" (").append(entry.getValue().details()).append(")");
			}
			reportBuilder.append(System.lineSeparator());
		}

		try {
			Files.writeString(evidenceDir.resolve("final-report.txt"), reportBuilder.toString());
		} catch (IOException ex) {
			throw new RuntimeException("Could not write final workflow report.", ex);
		}
	}

	private void assertInputPresent(final String inputLabel) {
		final Locator input = findInput(inputLabel);
		assertNotNull("Input field was not found for label: " + inputLabel, input);
		assertTrue("Input field is not visible for label: " + inputLabel, input.isVisible());
	}

	private Locator findInput(final String label) {
		final Pattern pattern = caseInsensitiveLiteralPattern(label);
		final Locator byLabel = page.getByLabel(pattern).first();
		if (byLabel.isVisible()) {
			return byLabel;
		}

		final Locator byPlaceholder = page.getByPlaceholder(pattern).first();
		if (byPlaceholder.isVisible()) {
			return byPlaceholder;
		}

		return null;
	}

	private void assertAnyTextVisible(final String failureMessage, final String... candidates) {
		assertAnyTextVisibleOnPage(page, failureMessage, candidates);
	}

	private void assertAnyTextVisibleOnPage(final Page targetPage, final String failureMessage, final String... candidates) {
		for (final String candidate : candidates) {
			if (targetPage.getByText(caseInsensitiveLiteralPattern(candidate)).first().isVisible()) {
				return;
			}
		}
		throw new AssertionError(failureMessage + " Candidates: " + Arrays.toString(candidates));
	}

	private void assertPatternVisible(final String failureMessage, final Pattern pattern) {
		final Locator locator = page.getByText(pattern).first();
		assertVisible(locator, failureMessage);
	}

	private void assertVisible(final Locator locator, final String failureMessage) {
		try {
			locator.waitFor(
					new Locator.WaitForOptions().setTimeout(DEFAULT_TIMEOUT_MS).setState(WaitForSelectorState.VISIBLE));
		} catch (PlaywrightException ex) {
			throw new AssertionError(failureMessage, ex);
		}
		assertTrue(failureMessage, locator.isVisible());
	}

	private void clickAndWait(final Locator locator) {
		assertNotNull("Attempted to click a null locator.", locator);
		locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUiLoad(page);
	}

	private void waitForUiLoad(final Page targetPage) {
		targetPage.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		try {
			targetPage.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7_000));
		} catch (PlaywrightException ignored) {
			// NETWORKIDLE is best effort: some apps keep websocket polling open.
		}
	}

	private Locator waitForAnyVisibleElement(final String[] textCandidates) {
		final long deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS;

		while (System.currentTimeMillis() < deadline) {
			for (final String candidate : textCandidates) {
				final Pattern pattern = caseInsensitiveLiteralPattern(candidate);
				final Locator byButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern)).first();
				if (byButton.isVisible()) {
					return byButton;
				}

				final Locator byLink = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern)).first();
				if (byLink.isVisible()) {
					return byLink;
				}

				final Locator byText = page.getByText(pattern).first();
				if (byText.isVisible()) {
					return byText;
				}
			}
			page.waitForTimeout(250);
		}

		return null;
	}

	private boolean isAnyTextVisible(final String text) {
		return page.getByText(caseInsensitiveLiteralPattern(text)).first().isVisible();
	}

	private void captureScreenshot(final String checkpointName, final boolean fullPage, final Page targetPage) {
		targetPage.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(checkpointName + ".png")).setFullPage(fullPage));
	}

	private boolean looksLikeUserNameIsVisible(final String bodyText) {
		final List<String> forbidden = List.of("informacion general", "información general", "detalles de la cuenta", "tus negocios",
				"seccion legal", "sección legal", "business plan", "cambiar plan", "cuenta creada", "estado activo",
				"idioma seleccionado");

		for (final String line : bodyText.split("\\R")) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.contains("@")) {
				continue;
			}

			final String normalized = trimmed.toLowerCase(Locale.ROOT);
			if (forbidden.contains(normalized)) {
				continue;
			}

			if (trimmed.matches(".*\\p{L}.*\\s+.*\\p{L}.*")) {
				return true;
			}
		}

		return false;
	}

	private String resolveLoginUrl() {
		final String propertyUrl = System.getProperty("saleads.login.url");
		final String environmentUrl = System.getenv("SALEADS_LOGIN_URL");
		final String loginUrl = propertyUrl != null ? propertyUrl : environmentUrl;
		assertNotNull("Set -Dsaleads.login.url or SALEADS_LOGIN_URL with the current environment login page URL.", loginUrl);
		assertTrue("Login URL must not be blank.", !loginUrl.isBlank());
		return loginUrl;
	}

	private String sanitize(final String rawName) {
		return rawName.replaceAll("[^a-z0-9\\-]", "-").replaceAll("-{2,}", "-");
	}

	private Pattern caseInsensitiveLiteralPattern(final String value) {
		return Pattern.compile(Pattern.quote(value), CASE_INSENSITIVE_FLAGS);
	}

	private interface StepExecutable {
		String run() throws Exception;
	}

	private record StepOutcome(boolean passed, String details) {
		private static StepOutcome pass(final String details) {
			return new StepOutcome(true, details);
		}

		private static StepOutcome fail(final String details) {
			return new StepOutcome(false, details == null ? "No details available." : details);
		}
	}
}
