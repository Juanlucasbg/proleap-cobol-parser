package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

/**
 * Full E2E validation for the SaleADS "Mi Negocio" module.
 *
 * <p>
 * This test is environment-agnostic. It requires SALEADS_BASE_URL to be provided externally and
 * uses visible text selectors whenever possible.
 * </p>
 */
public class SaleadsMiNegocioFullTest {

	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final int DEFAULT_TIMEOUT_MS = 20_000;
	private static final int CLICK_SETTLE_MS = 500;
	private static final String REPORT_FILE_NAME = "final-report.txt";
	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad");

	private final Map<String, String> finalReport = new LinkedHashMap<>();
	private Path evidenceDir;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String baseUrl = env("SALEADS_BASE_URL", "").trim();
		Assume.assumeTrue("SALEADS_BASE_URL must be set to run SaleADS E2E test.", !baseUrl.isEmpty());

		final String googleAccount = env("SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT);
		final boolean headless = Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"));
		final int timeoutMs = Integer.parseInt(env("SALEADS_TIMEOUT_MS", String.valueOf(DEFAULT_TIMEOUT_MS)));
		initializeReport();
		this.evidenceDir = Files.createDirectories(
				Path.of("target", "saleads-mi-negocio", "run-" + LocalDateTime.now().format(TS_FORMAT)));

		boolean allPassed = true;
		String failureMessage = "";

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(100));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
			context.setDefaultTimeout(timeoutMs);
			final Page appPage = context.newPage();

			appPage.navigate(baseUrl);
			waitForUi(appPage);

			allPassed &= executeStep("Login", () -> {
				loginWithGoogleIfNeeded(context, appPage, googleAccount);
				assertTrue("Main app interface is not visible.", looksLikeMainApplication(appPage));
				assertTrue("Left sidebar is not visible.", sidebarVisible(appPage));
				screenshot(appPage, "01-dashboard-loaded.png");
			});

			allPassed &= executeStep("Mi Negocio menu", () -> {
				openMiNegocioMenu(appPage);
				assertTextVisible(appPage, "Agregar Negocio");
				assertTextVisible(appPage, "Administrar Negocios");
				screenshot(appPage, "02-mi-negocio-menu-expanded.png");
			});

			allPassed &= executeStep("Agregar Negocio modal", () -> {
				clickByText(appPage, "Agregar Negocio");
				waitForUi(appPage);
				assertTextVisible(appPage, "Crear Nuevo Negocio");
				assertTextVisible(appPage, "Nombre del Negocio");
				assertTextVisible(appPage, "Tienes 2 de 3 negocios");
				assertTextVisible(appPage, "Cancelar");
				assertTextVisible(appPage, "Crear Negocio");
				screenshot(appPage, "03-agregar-negocio-modal.png");

				// Optional action: fill and close the modal to keep state clean for the next steps.
				Optional<Locator> nameInput = firstVisible(appPage,
						appPage.getByLabel("Nombre del Negocio"),
						appPage.getByPlaceholder("Nombre del Negocio"),
						appPage.locator("input").filter(new Locator.FilterOptions().setHasText("")));
				if (nameInput.isPresent()) {
					nameInput.get().fill("Negocio Prueba Automatizacion");
				}
				clickByText(appPage, "Cancelar");
				waitForUi(appPage);
			});

			allPassed &= executeStep("Administrar Negocios view", () -> {
				if (!isTextVisible(appPage, "Administrar Negocios")) {
					openMiNegocioMenu(appPage);
				}
				clickByText(appPage, "Administrar Negocios");
				waitForUi(appPage);
				assertTextVisible(appPage, "Informacion General", "Información General");
				assertTextVisible(appPage, "Detalles de la Cuenta");
				assertTextVisible(appPage, "Tus Negocios");
				assertTextVisible(appPage, "Seccion Legal", "Sección Legal");
				screenshot(appPage, "04-administrar-negocios-page.png", true);
			});

			allPassed &= executeStep("Información General", () -> {
				assertTrue("No user name candidate text found.",
						isTextVisibleRegex(appPage, "[A-Z][a-zA-Z]+\\s+[A-Z][a-zA-Z]+"));
				assertTrue("No user email found.", isTextVisibleRegex(appPage, "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"));
				assertTextVisible(appPage, "BUSINESS PLAN");
				assertTextVisible(appPage, "Cambiar Plan");
			});

			allPassed &= executeStep("Detalles de la Cuenta", () -> {
				assertTextVisible(appPage, "Cuenta creada");
				assertTextVisible(appPage, "Estado activo");
				assertTextVisible(appPage, "Idioma seleccionado");
			});

			allPassed &= executeStep("Tus Negocios", () -> {
				assertTextVisible(appPage, "Tus Negocios");
				assertTextVisible(appPage, "Agregar Negocio");
				assertTextVisible(appPage, "Tienes 2 de 3 negocios");
				assertTrue("Business list container is not visible.", looksLikeBusinessListVisible(appPage));
			});

			allPassed &= executeStep("Términos y Condiciones", () -> {
				termsUrl = validateLegalDocument(context, appPage,
						Arrays.asList("Terminos y Condiciones", "Términos y Condiciones"),
						Arrays.asList("Terminos y Condiciones", "Términos y Condiciones"),
						"05-terminos-y-condiciones.png");
			});

			allPassed &= executeStep("Política de Privacidad", () -> {
				privacyUrl = validateLegalDocument(context, appPage,
						Arrays.asList("Politica de Privacidad", "Política de Privacidad"),
						Arrays.asList("Politica de Privacidad", "Política de Privacidad"),
						"06-politica-de-privacidad.png");
			});
		} catch (Throwable t) {
			allPassed = false;
			failureMessage = t.getMessage() == null ? t.toString() : t.getMessage();
		}

		writeFinalReport();
		assertTrue(buildAssertionMessage(failureMessage), allPassed);
	}

	private void loginWithGoogleIfNeeded(final BrowserContext context, final Page appPage, final String accountEmail) {
		if (sidebarVisible(appPage)) {
			return;
		}

		final List<String> loginTexts = Arrays.asList(
				"Sign in with Google",
				"Iniciar sesion con Google",
				"Iniciar sesión con Google",
				"Continuar con Google",
				"Google");

		final Locator loginButton = findVisibleByTexts(appPage, loginTexts)
				.orElseThrow(() -> new AssertionError("Google login button is not visible on the login page."));

		Page googlePage = null;
		try {
			googlePage = appPage.waitForPopup(new Page.WaitForPopupOptions().setTimeout(7_000), loginButton::click);
		} catch (TimeoutError popupTimeout) {
			loginButton.click();
		}

		final Page authPage = googlePage == null ? appPage : googlePage;
		waitForUi(authPage);

		if (isTextVisible(authPage, accountEmail)) {
			clickByText(authPage, accountEmail);
			waitForUi(authPage);
		}

		if (googlePage != null && !googlePage.isClosed()) {
			googlePage.waitForLoadState(LoadState.NETWORKIDLE);
		}

		waitForUi(appPage);
	}

	private void openMiNegocioMenu(final Page page) {
		if (!isTextVisible(page, "Mi Negocio")) {
			clickByText(page, "Negocio");
		}
		clickByText(page, "Mi Negocio");
		waitForUi(page);
	}

	private String validateLegalDocument(
			final BrowserContext context,
			final Page appPage,
			final List<String> linkTexts,
			final List<String> titleTexts,
			final String screenshotName) {
		final String appUrlBefore = appPage.url();
		final Locator link = findVisibleByTexts(appPage, linkTexts)
				.orElseThrow(() -> new AssertionError("Legal link is not visible: " + linkTexts));

		Page target = null;
		try {
			target = appPage.waitForPopup(new Page.WaitForPopupOptions().setTimeout(7_000), link::click);
		} catch (TimeoutError popupTimeout) {
			link.click();
			target = appPage;
		}

		waitForUi(target);
		assertTrue("Expected legal title was not found: " + titleTexts, anyTextVisible(target, titleTexts));
		assertTrue("Legal content text is not visible.", hasLegalBodyContent(target));

		screenshot(target, screenshotName);
		final String finalUrl = target.url();

		if (target != appPage && !target.isClosed()) {
			target.close();
			appPage.bringToFront();
		} else if (!sameUrlIgnoreHash(appUrlBefore, appPage.url())) {
			try {
				appPage.goBack();
				waitForUi(appPage);
			} catch (RuntimeException ignored) {
				// Some browser contexts may not keep history for this navigation.
			}
			if (!sameUrlIgnoreHash(appUrlBefore, appPage.url())) {
				appPage.navigate(appUrlBefore);
				waitForUi(appPage);
			}
		}

		// Context is passed in to make tab/page ownership explicit in this step.
		assertTrue("No browser pages available after legal navigation.", !context.pages().isEmpty());
		return finalUrl;
	}

	private boolean hasLegalBodyContent(final Page page) {
		final String bodyText = page.locator("body").innerText();
		return bodyText != null && bodyText.replaceAll("\\s+", " ").trim().length() > 120;
	}

	private boolean looksLikeMainApplication(final Page page) {
		return sidebarVisible(page)
				|| anyTextVisible(page, Arrays.asList("Mi Negocio", "Negocio", "Dashboard", "Panel"));
	}

	private boolean sidebarVisible(final Page page) {
		return page.locator("aside").first().isVisible()
				|| page.getByRole(AriaRole.NAVIGATION).first().isVisible()
				|| isTextVisible(page, "Negocio");
	}

	private boolean looksLikeBusinessListVisible(final Page page) {
		return page.locator("table").first().isVisible()
				|| page.locator("ul li").first().isVisible()
				|| page.locator("[class*='business']").first().isVisible();
	}

	private void assertTextVisible(final Page page, final String... textCandidates) {
		assertTrue("None of expected text candidates are visible: " + Arrays.toString(textCandidates),
				anyTextVisible(page, Arrays.asList(textCandidates)));
	}

	private boolean anyTextVisible(final Page page, final List<String> textCandidates) {
		for (String candidate : textCandidates) {
			if (isTextVisible(page, candidate)) {
				return true;
			}
		}
		return false;
	}

	private boolean isTextVisibleRegex(final Page page, final String regex) {
		try {
			return page.locator("text=/" + regex + "/").first().isVisible();
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private boolean isTextVisible(final Page page, final String text) {
		try {
			if (page.getByText(text, new Page.GetByTextOptions().setExact(true)).first().isVisible()) {
				return true;
			}
		} catch (RuntimeException ignored) {
			// Try non-exact fallbacks below.
		}

		try {
			if (page.getByText(text).first().isVisible()) {
				return true;
			}
		} catch (RuntimeException ignored) {
			// Role-based fallback below.
		}

		try {
			return findVisibleByTexts(page, Arrays.asList(text)).isPresent();
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private void clickByText(final Page page, final String text) {
		final Locator locator = findVisibleByTexts(page, Arrays.asList(text))
				.orElseThrow(() -> new AssertionError("Clickable element with visible text not found: " + text));
		locator.click();
		waitForUi(page);
	}

	@SafeVarargs
	private final Optional<Locator> firstVisible(final Page page, final Locator... locators) {
		for (Locator locator : locators) {
			try {
				if (locator != null && locator.first().isVisible()) {
					return Optional.of(locator.first());
				}
			} catch (RuntimeException ignored) {
				// Continue probing candidates.
			}
		}

		return Optional.empty();
	}

	private Optional<Locator> findVisibleByTexts(final Page page, final List<String> texts) {
		for (String text : texts) {
			final Optional<Locator> roleFirst = firstVisible(
					page,
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text).setExact(true)),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text).setExact(true)),
					page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(text).setExact(true)),
					page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(text).setExact(true)),
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text)),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text)),
					page.getByText(text, new Page.GetByTextOptions().setExact(true)),
					page.getByText(text));
			if (roleFirst.isPresent()) {
				return roleFirst;
			}
		}

		return Optional.empty();
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (RuntimeException ignored) {
			// Not all actions trigger domcontentloaded.
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (RuntimeException ignored) {
			// Not all screens become fully idle.
		}
		page.waitForTimeout(CLICK_SETTLE_MS);
	}

	private void screenshot(final Page page, final String fileName) {
		screenshot(page, fileName, false);
	}

	private void screenshot(final Page page, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName)).setFullPage(fullPage));
	}

	private boolean executeStep(final String key, final StepAction action) {
		try {
			action.run();
			finalReport.put(key, "PASS");
			return true;
		} catch (Throwable t) {
			finalReport.put(key, "FAIL - " + safeMessage(t));
			return false;
		}
	}

	private void initializeReport() {
		for (String field : REPORT_FIELDS) {
			finalReport.put(field, "NOT_EXECUTED");
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		builder.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator());
		builder.append(System.lineSeparator());

		for (Map.Entry<String, String> entry : finalReport.entrySet()) {
			builder.append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
		}

		builder.append(System.lineSeparator());
		builder.append("Terminos y Condiciones URL: ").append(termsUrl).append(System.lineSeparator());
		builder.append("Politica de Privacidad URL: ").append(privacyUrl).append(System.lineSeparator());

		Files.writeString(evidenceDir.resolve(REPORT_FILE_NAME), builder.toString());
	}

	private String buildAssertionMessage(final String failureMessage) {
		final StringBuilder builder = new StringBuilder("One or more validations failed.\n");
		for (Map.Entry<String, String> entry : finalReport.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
		}
		builder.append("- Terminos y Condiciones URL: ").append(termsUrl).append('\n');
		builder.append("- Politica de Privacidad URL: ").append(privacyUrl).append('\n');
		if (!failureMessage.isBlank()) {
			builder.append("- Unexpected test exception: ").append(failureMessage).append('\n');
		}
		return builder.toString();
	}

	private String env(final String key, final String fallback) {
		final String value = System.getenv(key);
		return value == null ? fallback : value;
	}

	private String safeMessage(final Throwable throwable) {
		final String message = throwable.getMessage();
		return message == null ? throwable.getClass().getSimpleName() : message;
	}

	private boolean sameUrlIgnoreHash(final String left, final String right) {
		return normalizeUrl(left).equals(normalizeUrl(right));
	}

	private String normalizeUrl(final String url) {
		return Pattern.compile("#.*$").matcher(url == null ? "" : url).replaceFirst("");
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
