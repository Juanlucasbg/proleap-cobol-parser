package io.proleap.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioFullTest {

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFORMACION_GENERAL = "Información General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Política de Privacidad";

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String TEST_BUSINESS_NAME = "Negocio Prueba Automatizacion";

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		Assume.assumeTrue("Set RUN_SALEADS_E2E=true to execute this E2E test.", readFlag("RUN_SALEADS_E2E", "run.saleads.e2e"));

		final String baseUrl = readSetting("SALEADS_BASE_URL", "saleads.baseUrl");
		Assume.assumeTrue("Set SALEADS_BASE_URL (or -Dsaleads.baseUrl) to the login page for the current environment.", baseUrl != null && !baseUrl.isBlank());

		final boolean headless = readFlagWithDefault("SALEADS_HEADLESS", "saleads.headless", true);
		final Path evidenceDirectory = prepareEvidenceDirectory();

		final Map<String, Boolean> finalReport = new LinkedHashMap<>();
		finalReport.put(REPORT_LOGIN, false);
		finalReport.put(REPORT_MI_NEGOCIO_MENU, false);
		finalReport.put(REPORT_AGREGAR_NEGOCIO_MODAL, false);
		finalReport.put(REPORT_ADMINISTRAR_NEGOCIOS_VIEW, false);
		finalReport.put(REPORT_INFORMACION_GENERAL, false);
		finalReport.put(REPORT_DETALLES_CUENTA, false);
		finalReport.put(REPORT_TUS_NEGOCIOS, false);
		finalReport.put(REPORT_TERMINOS, false);
		finalReport.put(REPORT_PRIVACIDAD, false);

		Throwable failure = null;
		try (Playwright playwright = Playwright.create();
				Browser browser = playwright.chromium()
						.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(150));
				BrowserContext context = browser.newContext()) {
			try {
				Page appPage = context.newPage();
				appPage.navigate(baseUrl);
				waitForUi(appPage);

				loginWithGoogle(appPage, context);
				validateMainInterface(appPage);
				captureScreenshot(appPage, evidenceDirectory, "01-dashboard-loaded", false);
				finalReport.put(REPORT_LOGIN, true);

				openMiNegocioMenu(appPage);
				validateMiNegocioExpanded(appPage);
				captureScreenshot(appPage, evidenceDirectory, "02-mi-negocio-menu-expanded", false);
				finalReport.put(REPORT_MI_NEGOCIO_MENU, true);

				validateAgregarNegocioModal(appPage, evidenceDirectory);
				finalReport.put(REPORT_AGREGAR_NEGOCIO_MODAL, true);

				openAdministrarNegocios(appPage);
				validateAdministrarNegociosSections(appPage);
				captureScreenshot(appPage, evidenceDirectory, "04-administrar-negocios", true);
				finalReport.put(REPORT_ADMINISTRAR_NEGOCIOS_VIEW, true);

				validateInformacionGeneral(appPage);
				finalReport.put(REPORT_INFORMACION_GENERAL, true);

				validateDetallesDeLaCuenta(appPage);
				finalReport.put(REPORT_DETALLES_CUENTA, true);

				validateTusNegocios(appPage);
				finalReport.put(REPORT_TUS_NEGOCIOS, true);

				validateLegalLink(appPage, context, "Términos y Condiciones",
						Pattern.compile("(?i)t[eé]rminos\\s+y\\s+condiciones"),
						"05-terminos-y-condiciones", evidenceDirectory);
				finalReport.put(REPORT_TERMINOS, true);

				validateLegalLink(appPage, context, "Política de Privacidad",
						Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad"),
						"06-politica-de-privacidad", evidenceDirectory);
				finalReport.put(REPORT_PRIVACIDAD, true);
			} catch (Throwable throwable) {
				failure = throwable;
			}
		}

		printFinalReport(finalReport);
		if (failure != null) {
			throw new AssertionError("E2E execution failed. Review logs and screenshots under target/saleads-evidence.", failure);
		}
		assertTrue("One or more required validations failed. Review logs and screenshots under target/saleads-evidence.",
				finalReport.values().stream().allMatch(Boolean::booleanValue));
	}

	private void loginWithGoogle(final Page page, final BrowserContext context) {
		Locator loginButton = findClickableByText(page, Pattern.compile("(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s*sesi[oó]n\\s*con\\s*google|continuar\\s*con\\s*google)"));

		Page googlePage = null;
		try {
			googlePage = context.waitForPage(() -> clickAndWait(page, loginButton),
					new BrowserContext.WaitForPageOptions().setTimeout(8000));
		} catch (PlaywrightException ignored) {
			// Some environments navigate in the same tab, so this is expected.
		}

		Page authPage = googlePage != null ? googlePage : page;
		waitForUi(authPage);

		Locator accountOption = authPage.getByText(Pattern.compile("(?i)^\\s*" + Pattern.quote(GOOGLE_ACCOUNT_EMAIL) + "\\s*$")).first();
		if (isVisible(accountOption, 6000)) {
			clickAndWait(authPage, accountOption);
		}

		waitForUi(page);
	}

	private void validateMainInterface(final Page page) {
		assertVisible(findText(page, Pattern.compile("(?i)(mi\\s+negocio|negocio|dashboard|panel)")), "Main interface did not load.");
		assertVisible(findText(page, Pattern.compile("(?i)negocio")), "Left sidebar navigation is not visible.");
	}

	private void openMiNegocioMenu(final Page page) {
		Locator miNegocio = findClickableByText(page, Pattern.compile("(?i)^\\s*mi\\s+negocio\\s*$"));
		clickAndWait(page, miNegocio);
	}

	private void validateMiNegocioExpanded(final Page page) {
		assertVisible(findText(page, Pattern.compile("(?i)agregar\\s+negocio")), "'Agregar Negocio' is not visible.");
		assertVisible(findText(page, Pattern.compile("(?i)administrar\\s+negocios")), "'Administrar Negocios' is not visible.");
	}

	private void validateAgregarNegocioModal(final Page page, final Path evidenceDirectory) {
		Locator agregarNegocio = findClickableByText(page, Pattern.compile("(?i)^\\s*agregar\\s+negocio\\s*$"));
		clickAndWait(page, agregarNegocio);

		assertVisible(findText(page, Pattern.compile("(?i)crear\\s+nuevo\\s+negocio")), "Modal title 'Crear Nuevo Negocio' is missing.");
		assertVisible(findText(page, Pattern.compile("(?i)nombre\\s+del\\s+negocio")), "Input label 'Nombre del Negocio' is missing.");
		assertVisible(findText(page, Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios")), "Business quota text is missing.");
		assertVisible(findText(page, Pattern.compile("(?i)^\\s*cancelar\\s*$")), "Button 'Cancelar' is missing.");
		assertVisible(findText(page, Pattern.compile("(?i)crear\\s+negocio")), "Button 'Crear Negocio' is missing.");

		Locator nameInput = page.getByLabel(Pattern.compile("(?i)nombre\\s+del\\s+negocio")).first();
		if (isVisible(nameInput, 2000)) {
			nameInput.click();
			nameInput.fill(TEST_BUSINESS_NAME);
			waitForUi(page);
		}

		captureScreenshot(page, evidenceDirectory, "03-agregar-negocio-modal", false);

		Locator cancelar = findClickableByText(page, Pattern.compile("(?i)^\\s*cancelar\\s*$"));
		clickAndWait(page, cancelar);
	}

	private void openAdministrarNegocios(final Page page) {
		if (!isVisible(findText(page, Pattern.compile("(?i)administrar\\s+negocios")), 2000)) {
			openMiNegocioMenu(page);
		}
		Locator administrarNegocios = findClickableByText(page, Pattern.compile("(?i)^\\s*administrar\\s+negocios\\s*$"));
		clickAndWait(page, administrarNegocios);
	}

	private void validateAdministrarNegociosSections(final Page page) {
		assertVisible(findText(page, Pattern.compile("(?i)informaci[oó]n\\s+general")), "Section 'Información General' is missing.");
		assertVisible(findText(page, Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta")), "Section 'Detalles de la Cuenta' is missing.");
		assertVisible(findText(page, Pattern.compile("(?i)tus\\s+negocios")), "Section 'Tus Negocios' is missing.");
		assertVisible(findText(page, Pattern.compile("(?i)secci[oó]n\\s+legal")), "Section 'Sección Legal' is missing.");
	}

	private void validateInformacionGeneral(final Page page) {
		assertVisible(findText(page, Pattern.compile("@")), "User email is not visible.");
		assertVisible(findText(page, Pattern.compile("(?i)business\\s+plan")), "Text 'BUSINESS PLAN' is not visible.");
		assertVisible(findText(page, Pattern.compile("(?i)cambiar\\s+plan")), "Button 'Cambiar Plan' is not visible.");

		String visibleText = page.locator("body").innerText();
		assertTrue("User name is not visible in 'Información General'.", containsLikelyUserName(visibleText));
	}

	private void validateDetallesDeLaCuenta(final Page page) {
		assertVisible(findText(page, Pattern.compile("(?i)cuenta\\s+creada")), "'Cuenta creada' is not visible.");
		assertVisible(findText(page, Pattern.compile("(?i)estado\\s+activo")), "'Estado activo' is not visible.");
		assertVisible(findText(page, Pattern.compile("(?i)idioma\\s+seleccionado")), "'Idioma seleccionado' is not visible.");
	}

	private void validateTusNegocios(final Page page) {
		assertVisible(findText(page, Pattern.compile("(?i)tus\\s+negocios")), "Business list section is not visible.");
		assertVisible(findText(page, Pattern.compile("(?i)^\\s*agregar\\s+negocio\\s*$")), "Button 'Agregar Negocio' is missing.");
		assertVisible(findText(page, Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios")), "Business quota text is not visible.");
	}

	private void validateLegalLink(final Page appPage, final BrowserContext context, final String linkText,
			final Pattern headingPattern, final String screenshotName, final Path evidenceDirectory) {
		String appUrlBefore = appPage.url();
		Locator link = findClickableByText(appPage, Pattern.compile("(?i)^\\s*" + Pattern.quote(linkText) + "\\s*$"));

		Page legalPage = appPage;
		boolean openedNewTab = false;
		try {
			legalPage = context.waitForPage(() -> clickAndWait(appPage, link), new BrowserContext.WaitForPageOptions().setTimeout(7000));
			openedNewTab = true;
		} catch (PlaywrightException ignored) {
			// Same-tab navigation is valid.
		}

		waitForUi(legalPage);
		assertVisible(findText(legalPage, headingPattern), "Heading '" + linkText + "' is not visible.");

		String bodyText = legalPage.locator("body").innerText();
		assertTrue("Legal content text is not visible for " + linkText + ".", bodyText != null && bodyText.trim().length() > 200);

		captureScreenshot(legalPage, evidenceDirectory, screenshotName, true);
		System.out.println("[EVIDENCE] " + linkText + " URL: " + legalPage.url());

		if (openedNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else if (!appPage.url().equals(appUrlBefore)) {
			appPage.goBack();
			waitForUi(appPage);
		}
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
		locator.first().click();
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(20000));
		} catch (PlaywrightException ignored) {
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(12000));
		} catch (PlaywrightException ignored) {
			// Some apps keep long-polling; DOM content loaded is enough in those cases.
		}
	}

	private Locator findClickableByText(final Page page, final Pattern pattern) {
		Locator[] candidates = new Locator[] {
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern)).first(),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern)).first(),
				page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(pattern)).first(),
				page.getByText(pattern).first()
		};

		for (Locator candidate : candidates) {
			if (isVisible(candidate, 2000)) {
				return candidate;
			}
		}

		return page.getByText(pattern).first();
	}

	private Locator findText(final Page page, final Pattern pattern) {
		Locator[] candidates = new Locator[] {
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(pattern)).first(),
				page.getByText(pattern).first()
		};

		for (Locator candidate : candidates) {
			if (isVisible(candidate, 2000)) {
				return candidate;
			}
		}

		return page.getByText(pattern).first();
	}

	private void assertVisible(final Locator locator, final String errorMessage) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
		} catch (PlaywrightException ex) {
			throw new AssertionError(errorMessage, ex);
		}
	}

	private boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (PlaywrightException ex) {
			return false;
		}
	}

	private Path prepareEvidenceDirectory() throws IOException {
		String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		Path directory = Paths.get("target", "saleads-evidence", runId);
		Files.createDirectories(directory);
		return directory;
	}

	private void captureScreenshot(final Page page, final Path evidenceDirectory, final String checkpoint, final boolean fullPage) {
		String fileName = normalize(checkpoint) + ".png";
		Path screenshotPath = evidenceDirectory.resolve(fileName);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
		System.out.println("[EVIDENCE] Screenshot: " + screenshotPath.toAbsolutePath());
	}

	private String normalize(final String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("[^a-zA-Z0-9-]+", "-").replaceAll("-+", "-").toLowerCase();
	}

	private void printFinalReport(final Map<String, Boolean> report) {
		System.out.println("========== FINAL REPORT ==========");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		System.out.println("==================================");
	}

	private boolean containsLikelyUserName(final String text) {
		if (text == null) {
			return false;
		}

		String[] lines = text.split("\\R");
		for (String line : lines) {
			String cleaned = line.trim();
			if (cleaned.length() >= 5 && cleaned.length() <= 80 && cleaned.contains(" ") && !cleaned.contains("@")
					&& cleaned.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return true;
			}
		}
		return false;
	}

	private String readSetting(final String envKey, final String propertyKey) {
		String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}
		String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}
		return null;
	}

	private boolean readFlag(final String envKey, final String propertyKey) {
		return Boolean.parseBoolean(readSetting(envKey, propertyKey));
	}

	private boolean readFlagWithDefault(final String envKey, final String propertyKey, final boolean defaultValue) {
		String raw = readSetting(envKey, propertyKey);
		if (raw == null) {
			return defaultValue;
		}
		return Boolean.parseBoolean(raw);
	}

}
