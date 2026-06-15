package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Test;

public class SaleAdsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern LOGIN_WITH_GOOGLE = Pattern
			.compile("(?i)(sign\\s*in|log\\s*in|iniciar\\s*sesi[o\\u00F3]n|continuar).*google");
	private static final Pattern NEGOCIO_TEXT = Pattern.compile("(?i)^\\s*negocio\\s*$");
	private static final Pattern MI_NEGOCIO_TEXT = Pattern.compile("(?i)mi\\s+negocio");
	private static final Pattern AGREGAR_NEGOCIO_TEXT = Pattern.compile("(?i)agregar\\s+negocio");
	private static final Pattern ADMINISTRAR_NEGOCIOS_TEXT = Pattern.compile("(?i)administrar\\s+negocios");
	private static final Pattern CREAR_NUEVO_NEGOCIO_TEXT = Pattern.compile("(?i)crear\\s+nuevo\\s+negocio");
	private static final Pattern NOMBRE_DEL_NEGOCIO_TEXT = Pattern.compile("(?i)nombre\\s+del\\s+negocio");
	private static final Pattern BUSINESS_LIMIT_TEXT = Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios");
	private static final Pattern CANCELAR_TEXT = Pattern.compile("(?i)cancelar");
	private static final Pattern CREAR_NEGOCIO_TEXT = Pattern.compile("(?i)crear\\s+negocio");
	private static final Pattern INFORMACION_GENERAL_TEXT = Pattern.compile("(?i)informaci[o\\u00F3]n\\s+general");
	private static final Pattern DETALLES_CUENTA_TEXT = Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta");
	private static final Pattern TUS_NEGOCIOS_TEXT = Pattern.compile("(?i)tus\\s+negocios");
	private static final Pattern SECCION_LEGAL_TEXT = Pattern.compile("(?i)secci[o\\u00F3]n\\s+legal");
	private static final Pattern BUSINESS_PLAN_TEXT = Pattern.compile("(?i)business\\s+plan");
	private static final Pattern CAMBIAR_PLAN_TEXT = Pattern.compile("(?i)cambiar\\s+plan");
	private static final Pattern CUENTA_CREADA_TEXT = Pattern.compile("(?i)cuenta\\s+creada");
	private static final Pattern ESTADO_ACTIVO_TEXT = Pattern.compile("(?i)estado\\s+activo");
	private static final Pattern IDIOMA_SELECCIONADO_TEXT = Pattern.compile("(?i)idioma\\s+seleccionado");
	private static final Pattern TERM_LINK_TEXT = Pattern.compile("(?i)t(?:e|\\u00E9)rminos\\s+y\\s+condiciones");
	private static final Pattern POLICY_LINK_TEXT = Pattern.compile("(?i)pol(?:i|\\u00ED)tica\\s+de\\s+privacidad");
	private static final Pattern EMAIL_TEXT = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private Path artifactsDir;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String startUrl = firstNonBlank(System.getenv("SALEADS_LOGIN_URL"), System.getenv("SALEADS_START_URL"),
				System.getProperty("saleads.login.url"), System.getProperty("saleads.start.url"));

		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL or SALEADS_START_URL (or -Dsaleads.login.url) to execute this workflow test.",
				startUrl != null && !startUrl.isBlank());

		artifactsDir = Paths.get(firstNonBlank(System.getProperty("saleads.artifacts.dir"), "target/saleads-artifacts"));
		Files.createDirectories(artifactsDir);

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(resolveHeadless()));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
			final Page appPage = context.newPage();

			appPage.navigate(startUrl);
			waitForUi(appPage);

			runStep("Login", () -> {
				loginWithGoogle(appPage);
				captureScreenshot(appPage, "01-dashboard-loaded.png", false);
			});

			runStep("Mi Negocio menu", () -> {
				expandMiNegocioMenu(appPage);
				assertTextVisible(appPage, AGREGAR_NEGOCIO_TEXT, "Expected 'Agregar Negocio' in sidebar submenu.");
				assertTextVisible(appPage, ADMINISTRAR_NEGOCIOS_TEXT, "Expected 'Administrar Negocios' in sidebar submenu.");
				captureScreenshot(appPage, "02-mi-negocio-expanded-menu.png", false);
			});

			runStep("Agregar Negocio modal", () -> {
				clickByText(appPage, AGREGAR_NEGOCIO_TEXT);
				assertTextVisible(appPage, CREAR_NUEVO_NEGOCIO_TEXT, "Expected 'Crear Nuevo Negocio' modal title.");
				assertBusinessNameInputPresent(appPage);
				assertTextVisible(appPage, BUSINESS_LIMIT_TEXT, "Expected business limit text 'Tienes 2 de 3 negocios'.");
				clickInputAndTypeOptionalValue(appPage);
				assertButtonVisible(appPage, CANCELAR_TEXT, "Expected 'Cancelar' button in modal.");
				assertButtonVisible(appPage, CREAR_NEGOCIO_TEXT, "Expected 'Crear Negocio' button in modal.");
				captureScreenshot(appPage, "03-agregar-negocio-modal.png", false);
				clickButtonByName(appPage, CANCELAR_TEXT);
			});

			runStep("Administrar Negocios view", () -> {
				expandMiNegocioMenu(appPage);
				clickByText(appPage, ADMINISTRAR_NEGOCIOS_TEXT);
				assertTextVisible(appPage, INFORMACION_GENERAL_TEXT, "Expected 'Informacion General' section.");
				assertTextVisible(appPage, DETALLES_CUENTA_TEXT, "Expected 'Detalles de la Cuenta' section.");
				assertTextVisible(appPage, TUS_NEGOCIOS_TEXT, "Expected 'Tus Negocios' section.");
				assertTextVisible(appPage, SECCION_LEGAL_TEXT, "Expected 'Seccion Legal' section.");
				captureScreenshot(appPage, "04-administrar-negocios-full-page.png", true);
			});

			runStep("Informacion General", () -> {
				assertTextVisible(appPage, EMAIL_TEXT, "Expected user email to be visible in account details.");
				assertTextVisible(appPage, BUSINESS_PLAN_TEXT, "Expected BUSINESS PLAN text.");
				assertButtonVisible(appPage, CAMBIAR_PLAN_TEXT, "Expected 'Cambiar Plan' button.");
				assertHasLikelyUserName(appPage);
			});

			runStep("Detalles de la Cuenta", () -> {
				assertTextVisible(appPage, CUENTA_CREADA_TEXT, "Expected 'Cuenta creada' text.");
				assertTextVisible(appPage, ESTADO_ACTIVO_TEXT, "Expected 'Estado activo' text.");
				assertTextVisible(appPage, IDIOMA_SELECCIONADO_TEXT, "Expected 'Idioma seleccionado' text.");
			});

			runStep("Tus Negocios", () -> {
				assertTextVisible(appPage, TUS_NEGOCIOS_TEXT, "Expected 'Tus Negocios' section heading.");
				assertButtonVisible(appPage, AGREGAR_NEGOCIO_TEXT, "Expected 'Agregar Negocio' button.");
				assertTextVisible(appPage, BUSINESS_LIMIT_TEXT, "Expected 'Tienes 2 de 3 negocios' text.");
			});

			runStep("Terminos y Condiciones", () -> {
				final String finalUrl = validateLegalLink(appPage, TERM_LINK_TEXT, TERM_LINK_TEXT,
						"08-terminos-y-condiciones.png");
				legalUrls.put("Terminos y Condiciones", finalUrl);
			});

			runStep("Politica de Privacidad", () -> {
				final String finalUrl = validateLegalLink(appPage, POLICY_LINK_TEXT, POLICY_LINK_TEXT,
						"09-politica-de-privacidad.png");
				legalUrls.put("Politica de Privacidad", finalUrl);
			});

			writeReport();
		}

		assertAllStepsPassed();
	}

	private void loginWithGoogle(final Page page) {
		clickLoginButton(page);
		selectGoogleAccountIfPrompted(page);
		waitForUi(page);

		final boolean hasSidebar = hasVisibleElement(page.locator("aside, nav"), 15000);
		assertTrue("Expected left sidebar navigation after login.", hasSidebar);
		assertTrue("Expected main interface after login.",
				isTextVisible(page, NEGOCIO_TEXT, 12000) || isTextVisible(page, MI_NEGOCIO_TEXT, 12000));
	}

	private void clickLoginButton(final Page page) {
		final Locator byRole = page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(LOGIN_WITH_GOOGLE));
		if (hasVisibleElement(byRole, 10000)) {
			byRole.first().click();
			waitForUi(page);
			return;
		}

		final Locator byText = page.getByText(LOGIN_WITH_GOOGLE);
		if (hasVisibleElement(byText, 10000)) {
			byText.first().click();
			waitForUi(page);
			return;
		}

		throw new AssertionError("Unable to find login button or 'Sign in with Google' action.");
	}

	private void selectGoogleAccountIfPrompted(final Page page) {
		final Locator accountLocator = page.getByText(Pattern.compile("(?i)" + Pattern.quote(GOOGLE_ACCOUNT_EMAIL)));
		if (hasVisibleElement(accountLocator, 7000)) {
			accountLocator.first().click();
			waitForUi(page);
		}
	}

	private void expandMiNegocioMenu(final Page page) {
		if (isTextVisible(page, AGREGAR_NEGOCIO_TEXT, 1200) && isTextVisible(page, ADMINISTRAR_NEGOCIOS_TEXT, 1200)) {
			return;
		}

		if (hasVisibleElement(page.getByText(NEGOCIO_TEXT), 4000)) {
			clickByText(page, NEGOCIO_TEXT);
		}
		if (hasVisibleElement(page.getByText(MI_NEGOCIO_TEXT), 4000)) {
			clickByText(page, MI_NEGOCIO_TEXT);
		}

		assertTextVisible(page, AGREGAR_NEGOCIO_TEXT, "Expected 'Agregar Negocio' after expanding menu.");
		assertTextVisible(page, ADMINISTRAR_NEGOCIOS_TEXT, "Expected 'Administrar Negocios' after expanding menu.");
	}

	private String validateLegalLink(final Page appPage, final Pattern linkPattern, final Pattern headingPattern,
			final String screenshotName) throws IOException {
		Page targetPage;
		try {
			targetPage = appPage.waitForPopup(() -> clickByTextWithoutWait(appPage, linkPattern),
					new Page.WaitForPopupOptions().setTimeout(6000));
		} catch (PlaywrightException popupNotCreated) {
			clickByText(appPage, linkPattern);
			targetPage = appPage;
		}

		waitForUi(targetPage);
		assertTextVisible(targetPage, headingPattern, "Expected legal page heading after click.");
		assertLegalContentVisible(targetPage);
		captureScreenshot(targetPage, screenshotName, true);

		final String finalUrl = targetPage.url();
		Files.writeString(artifactsDir.resolve(screenshotName.replace(".png", "-url.txt")), finalUrl + System.lineSeparator());

		if (targetPage != appPage) {
			targetPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			try {
				appPage.goBack(new Page.GoBackOptions().setTimeout(8000));
				waitForUi(appPage);
			} catch (PlaywrightException ignored) {
				// If browser history is unavailable, continue in current page context.
			}
		}

		return finalUrl;
	}

	private void assertLegalContentVisible(final Page page) {
		final Locator content = page.locator("article, section, p, li");
		assertTrue("Expected legal content text to be visible.", hasVisibleElement(content, 10000));
	}

	private void clickInputAndTypeOptionalValue(final Page page) {
		Locator input = page.getByLabel(NOMBRE_DEL_NEGOCIO_TEXT);
		if (!hasVisibleElement(input, 1500)) {
			input = page.getByPlaceholder(NOMBRE_DEL_NEGOCIO_TEXT);
		}
		if (!hasVisibleElement(input, 1500)) {
			input = page.locator("input[type='text'], input:not([type])");
		}
		assertTrue("Expected input field 'Nombre del Negocio'.", hasVisibleElement(input, 8000));
		input.first().click();
		input.first().fill("Negocio Prueba Automatizacion");
		waitForUi(page);
	}

	private void assertBusinessNameInputPresent(final Page page) {
		final Locator labeledInput = page.getByLabel(NOMBRE_DEL_NEGOCIO_TEXT);
		if (labeledInput.count() > 0 && hasVisibleElement(labeledInput, 4000)) {
			return;
		}

		final Locator placeholderInput = page.getByPlaceholder(NOMBRE_DEL_NEGOCIO_TEXT);
		if (placeholderInput.count() > 0 && hasVisibleElement(placeholderInput, 4000)) {
			return;
		}

		final Locator genericInput = page.locator("input[type='text'], input:not([type])");
		assertTrue("Expected modal input for 'Nombre del Negocio'.", hasVisibleElement(genericInput, 4000));
	}

	private void assertButtonVisible(final Page page, final Pattern buttonPattern, final String message) {
		final Locator button = page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(buttonPattern));
		assertTrue(message, hasVisibleElement(button, 6000));
	}

	private void clickButtonByName(final Page page, final Pattern buttonPattern) {
		final Locator button = page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(buttonPattern));
		if (hasVisibleElement(button, 4000)) {
			button.first().click();
			waitForUi(page);
			return;
		}

		clickByText(page, buttonPattern);
	}

	private void assertHasLikelyUserName(final Page page) {
		final Locator headingText = page.locator("h1, h2, h3, strong");
		assertTrue("Expected user name text to be visible in account panel.", hasVisibleElement(headingText, 6000));
	}

	private void clickByText(final Page page, final Pattern textPattern) {
		clickByTextWithoutWait(page, textPattern);
		waitForUi(page);
	}

	private void clickByTextWithoutWait(final Page page, final Pattern textPattern) {
		final Locator text = page.getByText(textPattern);
		assertTrue("Expected clickable text: " + textPattern.pattern(), hasVisibleElement(text, 12000));
		text.first().click();
	}

	private void assertTextVisible(final Page page, final Pattern textPattern, final String message) {
		final Locator text = page.getByText(textPattern);
		assertTrue(message, hasVisibleElement(text, 10000));
	}

	private boolean isTextVisible(final Page page, final Pattern pattern, final int timeoutMs) {
		final Locator locator = page.getByText(pattern);
		return hasVisibleElement(locator, timeoutMs);
	}

	private boolean hasVisibleElement(final Locator locator, final int timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			try {
				if (locator.count() > 0) {
					locator.first().waitFor(
							new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(750));
					return true;
				}
			} catch (PlaywrightException ignored) {
				// Poll until timeout to absorb loading transitions.
			}
			try {
				Thread.sleep(150L);
			} catch (InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return false;
	}

	private void waitForUi(final Page page) {
		page.waitForTimeout(400);
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(6000));
		} catch (PlaywrightException ignored) {
			// DOM content loaded may not fire on SPA transitions.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(6000));
		} catch (PlaywrightException ignored) {
			// Network idle can be unavailable on apps with polling/XHR keepalive.
		}
		page.waitForTimeout(400);
	}

	private void captureScreenshot(final Page page, final String fileName, final boolean fullPage) throws IOException {
		final Path outputPath = artifactsDir.resolve(fileName);
		page.screenshot(new Page.ScreenshotOptions().setPath(outputPath).setFullPage(fullPage));
	}

	private void runStep(final String stepName, final CheckedStep step) {
		try {
			step.execute();
			report.put(stepName, new StepResult("PASS", ""));
		} catch (Throwable throwable) {
			final String message = firstNonBlank(throwable.getMessage(), throwable.getClass().getSimpleName());
			report.put(stepName, new StepResult("FAIL", message));
		}
	}

	private void writeReport() throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio workflow result").append(System.lineSeparator());
		sb.append("=================================").append(System.lineSeparator()).append(System.lineSeparator());
		report.forEach((step, stepResult) -> {
			sb.append(step).append(": ").append(stepResult.status);
			if (!stepResult.details.isBlank()) {
				sb.append(" - ").append(stepResult.details);
			}
			sb.append(System.lineSeparator());
		});

		if (!legalUrls.isEmpty()) {
			sb.append(System.lineSeparator()).append("Final legal URLs").append(System.lineSeparator());
			legalUrls.forEach((name, url) -> sb.append("- ").append(name).append(": ").append(url).append(System.lineSeparator()));
		}

		Files.writeString(artifactsDir.resolve("saleads-mi-negocio-report.txt"), sb.toString());
	}

	private void assertAllStepsPassed() {
		final String failedSteps = report.entrySet().stream().filter(entry -> !"PASS".equals(entry.getValue().status))
				.map(entry -> entry.getKey() + " (" + entry.getValue().details + ")").collect(Collectors.joining(", "));
		assertTrue("Workflow contains failed validations: " + failedSteps, failedSteps.isBlank());
	}

	private boolean resolveHeadless() {
		final String configured = firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"));
		return configured == null || Boolean.parseBoolean(configured);
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
		void execute() throws Exception;
	}

	private static final class StepResult {
		private final String status;
		private final String details;

		private StepResult(final String status, final String details) {
			this.status = status;
			this.details = details == null ? "" : details;
		}
	}
}
