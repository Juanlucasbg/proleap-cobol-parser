package io.proleap.saleads;

import static org.junit.Assert.assertTrue;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.Test;

public class SaleadsMiNegocioFullTest {

	private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Pattern LOGIN_BUTTON_PATTERN = Pattern.compile(
			"(?i)(sign in with google|iniciar sesi[oó]n con google|continuar con google|acceder con google|google)");
	private static final Pattern ENTRY_LOGIN_PATTERN = Pattern
			.compile("(?i)(login|log in|iniciar sesi[oó]n|sign in|acceder|entrar)");
	private static final Pattern MAIN_SIDEBAR_PATTERN = Pattern.compile("(?i)(mi negocio|negocio|dashboard|inicio)");
	private static final Pattern MI_NEGOCIO_PATTERN = Pattern.compile("(?i)mi negocio");
	private static final Pattern AGREGAR_NEGOCIO_PATTERN = Pattern.compile("(?i)agregar negocio");
	private static final Pattern ADMINISTRAR_NEGOCIOS_PATTERN = Pattern.compile("(?i)administrar negocios");
	private static final Pattern TERMINOS_PATTERN = Pattern.compile("(?i)t[ée]rminos y condiciones");
	private static final Pattern POLITICA_PATTERN = Pattern.compile("(?i)pol[ií]tica de privacidad");

	private final Map<String, String> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private Path artifactsDir;
	private Page currentPage;

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		artifactsDir = Paths.get("target", "saleads-mi-negocio", FILE_TS.format(LocalDateTime.now()));
		Files.createDirectories(artifactsDir);

		try (Playwright playwright = Playwright.create()) {
			boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
			try (Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
					BrowserContext context = browser.newContext()) {
				Page appPage = context.newPage();
				currentPage = appPage;

				String loginUrl = env("SALEADS_LOGIN_URL");
				appPage.navigate(loginUrl);
				waitForUi(appPage);

				boolean loginOk = runStep("Login", () -> doLoginWithGoogle(appPage), true);
				if (!loginOk) {
					failRemainingSteps();
					writeFinalReport();
					assertTrue(buildFailureMessage(), false);
					return;
				}

				runStep("Mi Negocio menu", () -> openMiNegocioMenu(appPage), true);
				runStep("Agregar Negocio modal", () -> validateAgregarNegocioModal(appPage), true);
				runStep("Administrar Negocios view", () -> openAdministrarNegocios(appPage), true);
				runStep("Información General", () -> validateInformacionGeneral(appPage), false);
				runStep("Detalles de la Cuenta", () -> validateDetallesCuenta(appPage), false);
				runStep("Tus Negocios", () -> validateTusNegocios(appPage), false);
				runStep("Términos y Condiciones", () -> validateLegalLink(appPage, TERMINOS_PATTERN, "terminos"), true);
				runStep("Política de Privacidad", () -> validateLegalLink(appPage, POLITICA_PATTERN, "politica"), true);

				writeFinalReport();
				assertTrue(buildFailureMessage(), report.values().stream().noneMatch(v -> v.startsWith("FAIL")));
			}
		}
	}

	private boolean runStep(String step, CheckedRunnable action, boolean screenshot) {
		try {
			action.run();
			report.put(step, "PASS");
			if (screenshot) {
				captureScreenshot("pass-" + toSlug(step), false);
			}
			return true;
		} catch (Throwable t) {
			report.put(step, "FAIL - " + t.getMessage());
			captureScreenshot("fail-" + toSlug(step), true);
			return false;
		}
	}

	private void doLoginWithGoogle(Page page) {
		Locator loginButton = findGoogleLoginLocator(page);
		if (!isVisible(loginButton)) {
			Locator entryLoginButton = page.getByRole(AriaRole.BUTTON,
					new Page.GetByRoleOptions().setName(ENTRY_LOGIN_PATTERN)).first();
			if (!isVisible(entryLoginButton)) {
				entryLoginButton = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(ENTRY_LOGIN_PATTERN))
						.first();
			}
			if (isVisible(entryLoginButton)) {
				clickAndWait(entryLoginButton, page);
				loginButton = findGoogleLoginLocator(page);
			}
		}
		waitVisible(loginButton, "Login button / Sign in with Google");
		clickAndWait(loginButton, page);

		selectGoogleAccountIfShown(page);
		waitForUi(page);

		waitVisible(page.getByText(MAIN_SIDEBAR_PATTERN).first(), "Main interface and sidebar");
		waitVisible(page.getByText(MI_NEGOCIO_PATTERN).first(), "Mi Negocio in left sidebar");
		captureScreenshot("dashboard-loaded", true);
	}

	private Locator findGoogleLoginLocator(Page page) {
		Locator loginButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(LOGIN_BUTTON_PATTERN))
				.first();
		if (!isVisible(loginButton)) {
			loginButton = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(LOGIN_BUTTON_PATTERN)).first();
		}
		if (!isVisible(loginButton)) {
			loginButton = page.getByText(LOGIN_BUTTON_PATTERN).first();
		}
		return loginButton;
	}

	private void openMiNegocioMenu(Page page) {
		Locator miNegocio = page.getByText(MI_NEGOCIO_PATTERN).first();
		waitVisible(miNegocio, "Mi Negocio menu entry");
		clickAndWait(miNegocio, page);

		waitVisible(page.getByText(AGREGAR_NEGOCIO_PATTERN).first(), "Agregar Negocio visible");
		waitVisible(page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN).first(), "Administrar Negocios visible");
		captureScreenshot("menu-mi-negocio-expandido", false);
	}

	private void validateAgregarNegocioModal(Page page) {
		Locator agregarNegocio = page.getByText(AGREGAR_NEGOCIO_PATTERN).first();
		waitVisible(agregarNegocio, "Agregar Negocio menu item");
		clickAndWait(agregarNegocio, page);

		waitVisible(page.getByText(Pattern.compile("(?i)crear nuevo negocio")).first(), "Crear Nuevo Negocio modal");
		waitVisible(page.getByLabel(Pattern.compile("(?i)nombre del negocio")).first(), "Nombre del Negocio input");
		waitVisible(page.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios")).first(),
				"2 of 3 businesses text");
		waitVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar"))).first(),
				"Cancelar button");
		waitVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear negocio"))).first(),
				"Crear Negocio button");
		captureScreenshot("modal-crear-negocio", false);

		Locator nombreInput = page.getByLabel(Pattern.compile("(?i)nombre del negocio")).first();
		clickAndWait(nombreInput, page);
		nombreInput.fill("Negocio Prueba Automatizacion");
		clickAndWait(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar"))).first(),
				page);
	}

	private void openAdministrarNegocios(Page page) {
		Locator administrar = page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN).first();
		if (!isVisible(administrar)) {
			Locator miNegocio = page.getByText(MI_NEGOCIO_PATTERN).first();
			waitVisible(miNegocio, "Mi Negocio menu re-open");
			clickAndWait(miNegocio, page);
			administrar = page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN).first();
		}

		waitVisible(administrar, "Administrar Negocios menu item");
		clickAndWait(administrar, page);

		waitVisible(page.getByText(Pattern.compile("(?i)informaci[oó]n general")).first(), "Informacion General section");
		waitVisible(page.getByText(Pattern.compile("(?i)detalles de la cuenta")).first(), "Detalles de la Cuenta section");
		waitVisible(page.getByText(Pattern.compile("(?i)tus negocios")).first(), "Tus Negocios section");
		waitVisible(page.getByText(Pattern.compile("(?i)secci[oó]n legal")).first(), "Seccion Legal section");
		captureScreenshot("administrar-negocios", true);
	}

	private void validateInformacionGeneral(Page page) {
		waitVisible(page.getByText(Pattern.compile("(?i)nombre|name")).first(), "User name visible");
		waitVisible(page.getByText(Pattern.compile("@")).first(), "User email visible");
		waitVisible(page.getByText(Pattern.compile("(?i)business plan")).first(), "BUSINESS PLAN text");
		waitVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cambiar plan"))).first(),
				"Cambiar Plan button");
	}

	private void validateDetallesCuenta(Page page) {
		waitVisible(page.getByText(Pattern.compile("(?i)cuenta creada")).first(), "Cuenta creada text");
		waitVisible(page.getByText(Pattern.compile("(?i)estado activo|activo")).first(), "Estado activo text");
		waitVisible(page.getByText(Pattern.compile("(?i)idioma seleccionado|idioma")).first(),
				"Idioma seleccionado text");
	}

	private void validateTusNegocios(Page page) {
		waitVisible(page.getByText(Pattern.compile("(?i)tus negocios")).first(), "Tus Negocios section title");
		waitVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)).first(),
				"Agregar Negocio button in business list");
		waitVisible(page.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios")).first(),
				"2 de 3 negocios text");
	}

	private void validateLegalLink(Page appPage, Pattern linkPattern, String slug) {
		Locator legalLink = appPage.getByText(linkPattern).first();
		waitVisible(legalLink, "Legal link " + linkPattern.pattern());

		Page targetPage = appPage;
		boolean openedPopup = false;
		try {
			targetPage = appPage.waitForPopup(new Page.WaitForPopupOptions().setTimeout(5000),
					() -> clickAndWait(legalLink, appPage));
			openedPopup = true;
		} catch (PlaywrightException timeout) {
			if (!timeout.getMessage().toLowerCase().contains("timeout")) {
				throw timeout;
			}
		}

		waitForUi(targetPage);
		waitVisible(targetPage.getByText(linkPattern).first(), "Legal heading " + linkPattern.pattern());
		waitVisible(targetPage.locator("body"), "Legal content body");
		String legalText = targetPage.locator("body").innerText();
		if (legalText == null || legalText.trim().length() < 80) {
			throw new AssertionError("Legal content text is not sufficiently visible");
		}
		currentPage = targetPage;
		captureScreenshot(targetPage, "legal-" + slug, true);
		legalUrls.put(slug, targetPage.url());

		if (openedPopup) {
			targetPage.close();
			appPage.bringToFront();
			currentPage = appPage;
			waitForUi(appPage);
		} else {
			targetPage.goBack();
			currentPage = appPage;
			waitForUi(appPage);
		}
	}

	private void selectGoogleAccountIfShown(Page page) {
		String email = System.getenv().getOrDefault("SALEADS_GOOGLE_ACCOUNT_EMAIL", DEFAULT_GOOGLE_EMAIL);
		Locator account = page.getByText(Pattern.compile("(?i)" + Pattern.quote(email))).first();
		if (isVisible(account)) {
			clickAndWait(account, page);
			return;
		}

		Locator chooseAccount = page.getByText(Pattern.compile("(?i)choose an account|elige una cuenta")).first();
		if (isVisible(chooseAccount)) {
			waitForUi(page);
		}
	}

	private void waitForUi(Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7000));
		} catch (PlaywrightException ignored) {
			// Some views keep websocket/network activity alive indefinitely.
		}
		page.waitForTimeout(700);
	}

	private void clickAndWait(Locator locator, Page page) {
		locator.click();
		waitForUi(page);
	}

	private void waitVisible(Locator locator, String description) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setTimeout(20000));
		} catch (PlaywrightException e) {
			throw new AssertionError("Missing expected element: " + description, e);
		}
	}

	private boolean isVisible(Locator locator) {
		try {
			return locator.isVisible();
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private void captureScreenshot(String filename, boolean fullPage) {
		captureScreenshot(currentPage, filename, fullPage);
	}

	private void captureScreenshot(Page page, String filename, boolean fullPage) {
		if (artifactsDir == null) {
			return;
		}
		Page shotPage = page;
		if (shotPage == null) {
			return;
		}
		Path path = artifactsDir.resolve(filename + ".png");
		shotPage.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private void failRemainingSteps() {
		for (String step : new String[] { "Mi Negocio menu", "Agregar Negocio modal", "Administrar Negocios view",
				"Información General", "Detalles de la Cuenta", "Tus Negocios", "Términos y Condiciones",
				"Política de Privacidad" }) {
			report.putIfAbsent(step, "FAIL - Blocked by login failure");
		}
	}

	private void writeFinalReport() throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio Full Test Report").append(System.lineSeparator());
		sb.append("Artifacts: ").append(artifactsDir.toAbsolutePath()).append(System.lineSeparator());
		for (Map.Entry<String, String> entry : report.entrySet()) {
			sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
		}
		for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
			sb.append("URL ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
		}
		Files.writeString(artifactsDir.resolve("final-report.txt"), sb.toString());
	}

	private String buildFailureMessage() {
		StringBuilder sb = new StringBuilder("One or more workflow validations failed:");
		for (Map.Entry<String, String> entry : report.entrySet()) {
			sb.append(System.lineSeparator()).append(entry.getKey()).append(" => ").append(entry.getValue());
		}
		return sb.toString();
	}

	private String env(String key) {
		String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Environment variable required: " + key);
		}
		return value;
	}

	private String toSlug(String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private interface CheckedRunnable {
		void run();
	}

}
