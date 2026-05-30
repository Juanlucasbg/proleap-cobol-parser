package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.microsoft.playwright.options.WaitUntilState;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String LOGIN_FIELD = "Login";
	private static final String MI_NEGOCIO_MENU_FIELD = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL_FIELD = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW_FIELD = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL_FIELD = "Información General";
	private static final String DETALLES_CUENTA_FIELD = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS_FIELD = "Tus Negocios";
	private static final String TERMINOS_FIELD = "Términos y Condiciones";
	private static final String PRIVACIDAD_FIELD = "Política de Privacidad";

	private final Map<String, StepResult> report = new LinkedHashMap<>();

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String startUrl = configuration("saleads.startUrl", "SALEADS_START_URL", null);
		Assume.assumeTrue("Provide SALEADS_START_URL or -Dsaleads.startUrl to run this flow.", startUrl != null && !startUrl.isBlank());

		final String googleAccount = configuration("saleads.googleAccount", "SALEADS_GOOGLE_ACCOUNT",
				"juanlucasbarbiergarzon@gmail.com");
		final boolean headless = Boolean.parseBoolean(configuration("saleads.headless", "SALEADS_HEADLESS", "true"));

		final Path evidenceDir = createEvidenceDir();
		initializeReport();

		String terminosUrl = "";
		String privacidadUrl = "";

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
			final Page appPage = context.newPage();

			appPage.navigate(startUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUi(appPage);

			runLoginStep(appPage, googleAccount, evidenceDir);
			runMiNegocioMenuStep(appPage, evidenceDir);
			runAgregarNegocioModalStep(appPage, evidenceDir);
			runAdministrarNegociosStep(appPage, evidenceDir);
			runInformacionGeneralValidation(appPage);
			runDetallesCuentaValidation(appPage);
			runTusNegociosValidation(appPage);
			terminosUrl = runLegalDocumentValidation(appPage, "Términos y Condiciones", evidenceDir, "08-terminos.png");
			privacidadUrl = runLegalDocumentValidation(appPage, "Política de Privacidad", evidenceDir, "09-politica-privacidad.png");

			context.close();
			browser.close();
		}

		final Path reportFile = writeFinalReport(evidenceDir, terminosUrl, privacidadUrl);
		System.out.println("Final report written to: " + reportFile.toAbsolutePath());
		System.out.println(buildOneLineSummary());
		assertTrue("Workflow had failing validations. See report: " + reportFile.toAbsolutePath(), failedFields().isEmpty());
	}

	private void runLoginStep(final Page appPage, final String googleAccount, final Path evidenceDir) {
		final StepResult result = report.get(LOGIN_FIELD);
		try {
			final Locator loginButton = locateClickableByName(appPage, "Sign in with Google", "Iniciar sesion con Google",
					"Iniciar sesión con Google", "Continuar con Google", "Google");
			if (loginButton == null) {
				result.fail("Could not locate login button ('Sign in with Google').");
				return;
			}

			Page googlePage = null;
			try {
				googlePage = appPage.waitForPopup(new Page.WaitForPopupOptions().setTimeout(8000),
						() -> clickAndWait(appPage, loginButton));
			} catch (final PlaywrightException noPopup) {
				clickAndWait(appPage, loginButton);
			}

			final Page interactionPage = googlePage != null ? googlePage : appPage;
			final Locator accountOption = interactionPage.getByText(googleAccount, new Page.GetByTextOptions().setExact(true));
			if (isVisible(accountOption, 8000)) {
				clickAndWait(interactionPage, accountOption);
			}

			waitForUi(appPage);
			final boolean mainInterfaceVisible = isVisible(appPage.locator("main"), 10000) || isTextVisible(appPage, "Negocio", 10000);
			final boolean sidebarVisible = isVisible(appPage.locator("aside"), 10000) || isTextVisible(appPage, "Mi Negocio", 10000);

			result.check(mainInterfaceVisible, "Main application interface is visible.", "Main application interface is not visible.");
			result.check(sidebarVisible, "Left sidebar navigation is visible.", "Left sidebar navigation is not visible.");
			capture(appPage, evidenceDir.resolve("01-dashboard.png"), true);
		} catch (final Exception ex) {
			result.fail("Login step failed with exception: " + ex.getMessage());
		}
	}

	private void runMiNegocioMenuStep(final Page appPage, final Path evidenceDir) {
		final StepResult result = report.get(MI_NEGOCIO_MENU_FIELD);
		try {
			result.check(isTextVisible(appPage, "Negocio", 5000), "Sidebar section 'Negocio' is visible.",
					"Sidebar section 'Negocio' is not visible.");
			clickByVisibleText(appPage, "Mi Negocio");
			final boolean submenuExpanded = isTextVisible(appPage, "Agregar Negocio", 8000)
					&& isTextVisible(appPage, "Administrar Negocios", 8000);
			result.check(submenuExpanded, "Mi Negocio submenu is expanded.", "Mi Negocio submenu did not expand.");
			result.check(isTextVisible(appPage, "Agregar Negocio", 3000), "'Agregar Negocio' is visible.",
					"'Agregar Negocio' is not visible.");
			result.check(isTextVisible(appPage, "Administrar Negocios", 3000), "'Administrar Negocios' is visible.",
					"'Administrar Negocios' is not visible.");
			capture(appPage, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), true);
		} catch (final Exception ex) {
			result.fail("Mi Negocio menu step failed with exception: " + ex.getMessage());
		}
	}

	private void runAgregarNegocioModalStep(final Page appPage, final Path evidenceDir) {
		final StepResult result = report.get(AGREGAR_NEGOCIO_MODAL_FIELD);
		try {
			clickByVisibleText(appPage, "Agregar Negocio");
			final boolean titleVisible = isTextVisible(appPage, "Crear Nuevo Negocio", 10000);
			result.check(titleVisible, "Modal title 'Crear Nuevo Negocio' is visible.",
					"Modal title 'Crear Nuevo Negocio' is missing.");

			Locator nombreInput = appPage.getByLabel("Nombre del Negocio");
			if (!isVisible(nombreInput, 3000)) {
				nombreInput = appPage.getByPlaceholder("Nombre del Negocio");
			}
			result.check(isVisible(nombreInput, 3000), "Input 'Nombre del Negocio' exists.", "Input 'Nombre del Negocio' is missing.");
			result.check(isTextVisible(appPage, "Tienes 2 de 3 negocios", 3000), "Usage text 'Tienes 2 de 3 negocios' is visible.",
					"Usage text 'Tienes 2 de 3 negocios' is missing.");
			result.check(isTextVisible(appPage, "Cancelar", 3000), "Button 'Cancelar' is present.", "Button 'Cancelar' is missing.");
			result.check(isTextVisible(appPage, "Crear Negocio", 3000), "Button 'Crear Negocio' is present.",
					"Button 'Crear Negocio' is missing.");

			capture(appPage, evidenceDir.resolve("03-agregar-negocio-modal.png"), true);

			if (isVisible(nombreInput, 1000)) {
				nombreInput.first().click();
				nombreInput.first().fill("Negocio Prueba Automatizacion");
			}
			clickByVisibleText(appPage, "Cancelar");
		} catch (final Exception ex) {
			result.fail("Agregar Negocio modal step failed with exception: " + ex.getMessage());
		}
	}

	private void runAdministrarNegociosStep(final Page appPage, final Path evidenceDir) {
		final StepResult result = report.get(ADMINISTRAR_NEGOCIOS_VIEW_FIELD);
		try {
			if (!isTextVisible(appPage, "Administrar Negocios", 2000)) {
				clickByVisibleText(appPage, "Mi Negocio");
			}
			clickByVisibleText(appPage, "Administrar Negocios");

			result.check(isTextVisible(appPage, "Información General", 10000), "Section 'Informacion General' exists.",
					"Section 'Informacion General' is missing.");
			result.check(isTextVisible(appPage, "Detalles de la Cuenta", 5000), "Section 'Detalles de la Cuenta' exists.",
					"Section 'Detalles de la Cuenta' is missing.");
			result.check(isTextVisible(appPage, "Tus Negocios", 5000), "Section 'Tus Negocios' exists.",
					"Section 'Tus Negocios' is missing.");
			result.check(isTextVisible(appPage, "Sección Legal", 5000), "Section 'Seccion Legal' exists.",
					"Section 'Seccion Legal' is missing.");
			capture(appPage, evidenceDir.resolve("04-administrar-negocios.png"), true);
		} catch (final Exception ex) {
			result.fail("Administrar Negocios step failed with exception: " + ex.getMessage());
		}
	}

	private void runInformacionGeneralValidation(final Page appPage) {
		final StepResult result = report.get(INFORMACION_GENERAL_FIELD);
		try {
			final boolean emailVisible = isEmailVisible(appPage, 5000);
			final boolean userNameVisible = isTextVisible(appPage, "Nombre", 2000) || isTextVisible(appPage, "Usuario", 2000) || emailVisible;

			result.check(userNameVisible, "User name indicator is visible.", "User name indicator is not visible.");
			result.check(emailVisible, "User email is visible.", "User email is not visible.");
			result.check(isTextVisible(appPage, "BUSINESS PLAN", 3000), "Text 'BUSINESS PLAN' is visible.",
					"Text 'BUSINESS PLAN' is missing.");
			result.check(isTextVisible(appPage, "Cambiar Plan", 3000), "Button 'Cambiar Plan' is visible.",
					"Button 'Cambiar Plan' is missing.");
		} catch (final Exception ex) {
			result.fail("Informacion General validation failed with exception: " + ex.getMessage());
		}
	}

	private void runDetallesCuentaValidation(final Page appPage) {
		final StepResult result = report.get(DETALLES_CUENTA_FIELD);
		try {
			result.check(isTextVisible(appPage, "Cuenta creada", 5000), "'Cuenta creada' is visible.", "'Cuenta creada' is missing.");
			result.check(isTextVisible(appPage, "Estado activo", 5000), "'Estado activo' is visible.", "'Estado activo' is missing.");
			result.check(isTextVisible(appPage, "Idioma seleccionado", 5000), "'Idioma seleccionado' is visible.",
					"'Idioma seleccionado' is missing.");
		} catch (final Exception ex) {
			result.fail("Detalles de la Cuenta validation failed with exception: " + ex.getMessage());
		}
	}

	private void runTusNegociosValidation(final Page appPage) {
		final StepResult result = report.get(TUS_NEGOCIOS_FIELD);
		try {
			final boolean listVisible = isTextVisible(appPage, "Tus Negocios", 4000) && isTextVisible(appPage, "Negocio", 4000);
			result.check(listVisible, "Business list is visible.", "Business list is not visible.");
			result.check(isTextVisible(appPage, "Agregar Negocio", 3000), "Button 'Agregar Negocio' exists.",
					"Button 'Agregar Negocio' is missing.");
			result.check(isTextVisible(appPage, "Tienes 2 de 3 negocios", 3000), "Text 'Tienes 2 de 3 negocios' is visible.",
					"Text 'Tienes 2 de 3 negocios' is missing.");
		} catch (final Exception ex) {
			result.fail("Tus Negocios validation failed with exception: " + ex.getMessage());
		}
	}

	private String runLegalDocumentValidation(final Page appPage, final String linkText, final Path evidenceDir, final String screenshotName) {
		final String stepKey = "Términos y Condiciones".equals(linkText) ? TERMINOS_FIELD : PRIVACIDAD_FIELD;
		final StepResult result = report.get(stepKey);
		String finalUrl = "";

		try {
			final String originalAppUrl = appPage.url();
			final Locator link = locateClickableByName(appPage, linkText);
			if (link == null) {
				result.fail("Could not locate link: " + linkText);
				return "";
			}

			Page legalPage = null;
			boolean openedNewTab = false;

			try {
				legalPage = appPage.waitForPopup(new Page.WaitForPopupOptions().setTimeout(5000),
						() -> clickAndWait(appPage, link));
				openedNewTab = true;
			} catch (final PlaywrightException noPopup) {
				clickAndWait(appPage, link);
				legalPage = appPage;
			}

			waitForUi(legalPage);
			finalUrl = legalPage.url();

			result.check(isTextVisible(legalPage, linkText, 10000), "Heading '" + linkText + "' is visible.",
					"Heading '" + linkText + "' is missing.");
			final String bodyText = legalPage.locator("body").innerText();
			final boolean legalContentVisible = bodyText != null && bodyText.trim().length() > 200;
			result.check(legalContentVisible, "Legal content text is visible.", "Legal content text is not visible.");
			result.notes.add("Final URL: " + finalUrl);
			capture(legalPage, evidenceDir.resolve(screenshotName), true);

			if (openedNewTab) {
				legalPage.close();
				appPage.bringToFront();
				waitForUi(appPage);
			} else {
				appPage.navigate(originalAppUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
				waitForUi(appPage);
			}
		} catch (final Exception ex) {
			result.fail(linkText + " validation failed with exception: " + ex.getMessage());
		}

		return finalUrl;
	}

	private void clickByVisibleText(final Page page, final String text) {
		final Locator locator = locateClickableByName(page, text);
		if (locator == null) {
			throw new IllegalStateException("Could not find clickable element by text: " + text);
		}
		clickAndWait(page, locator);
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.first().click(new Locator.ClickOptions().setTimeout(15000));
		waitForUi(page);
	}

	private Locator locateClickableByName(final Page page, final String... names) {
		final List<Locator> candidates = new ArrayList<>();
		for (final String name : names) {
			final Pattern exactPattern = Pattern.compile("^\\s*" + Pattern.quote(name) + "\\s*$", Pattern.CASE_INSENSITIVE);
			candidates.add(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(exactPattern)));
			candidates.add(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(exactPattern)));
			candidates.add(page.getByText(name, new Page.GetByTextOptions().setExact(true)));
		}

		for (final Locator candidate : candidates) {
			if (candidate.count() > 0 && isVisible(candidate.first(), 1500)) {
				return candidate.first();
			}
		}

		return null;
	}

	private boolean isTextVisible(final Page page, final String text, final int timeoutMs) {
		final Locator exactText = page.getByText(text, new Page.GetByTextOptions().setExact(true));
		if (isVisible(exactText, timeoutMs)) {
			return true;
		}
		final Locator fuzzyText = page.getByText(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE));
		return isVisible(fuzzyText, timeoutMs);
	}

	private boolean isEmailVisible(final Page page, final int timeoutMs) {
		final Locator emailLocator = page.getByText(Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE));
		return isVisible(emailLocator, timeoutMs);
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (final PlaywrightException notVisible) {
			return false;
		}
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8000));
		} catch (final PlaywrightException ignored) {
			// Some transitions are instant and never reach NETWORKIDLE.
		}
		page.waitForTimeout(600);
	}

	private void capture(final Page page, final Path target, final boolean fullPage) throws IOException {
		Files.createDirectories(target.getParent());
		page.screenshot(new Page.ScreenshotOptions().setPath(target).setFullPage(fullPage));
	}

	private Path createEvidenceDir() throws IOException {
		final String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		final Path dir = Paths.get("target", "saleads-mi-negocio", stamp);
		Files.createDirectories(dir);
		return dir;
	}

	private void initializeReport() {
		final List<String> keys = Arrays.asList(LOGIN_FIELD, MI_NEGOCIO_MENU_FIELD, AGREGAR_NEGOCIO_MODAL_FIELD,
				ADMINISTRAR_NEGOCIOS_VIEW_FIELD, INFORMACION_GENERAL_FIELD, DETALLES_CUENTA_FIELD, TUS_NEGOCIOS_FIELD,
				TERMINOS_FIELD, PRIVACIDAD_FIELD);
		for (final String key : keys) {
			report.put(key, new StepResult());
		}
	}

	private Path writeFinalReport(final Path evidenceDir, final String terminosUrl, final String privacidadUrl) throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("# SaleADS Mi Negocio Workflow Report\n\n");
		builder.append("| Field | Result | Notes |\n");
		builder.append("| --- | --- | --- |\n");
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			final String field = entry.getKey();
			final StepResult result = entry.getValue();
			builder.append("| ").append(field).append(" | ").append(result.pass ? "PASS" : "FAIL").append(" | ")
					.append(escapeForTable(String.join("; ", result.notes))).append(" |\n");
		}
		builder.append("\n## Legal URLs\n\n");
		builder.append("- Términos y Condiciones: ").append(terminosUrl == null ? "" : terminosUrl).append("\n");
		builder.append("- Política de Privacidad: ").append(privacidadUrl == null ? "" : privacidadUrl).append("\n");

		final Path reportPath = evidenceDir.resolve("final-report.md");
		Files.writeString(reportPath, builder.toString(), StandardCharsets.UTF_8);
		return reportPath;
	}

	private String buildOneLineSummary() {
		final StringBuilder summary = new StringBuilder("Final status: ");
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			summary.append("[").append(entry.getKey()).append("=").append(entry.getValue().pass ? "PASS" : "FAIL").append("] ");
		}
		return summary.toString().trim();
	}

	private List<String> failedFields() {
		final List<String> failures = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!entry.getValue().pass) {
				failures.add(entry.getKey());
			}
		}
		return failures;
	}

	private String configuration(final String systemProperty, final String envVar, final String defaultValue) {
		String value = System.getProperty(systemProperty);
		if (value == null || value.isBlank()) {
			value = System.getenv(envVar);
		}
		if (value == null || value.isBlank()) {
			value = defaultValue;
		}
		return value;
	}

	private String escapeForTable(final String text) {
		return text.replace("|", "\\|");
	}

	private static class StepResult {
		private boolean pass = true;
		private final List<String> notes = new ArrayList<>();

		private void check(final boolean condition, final String successNote, final String failureNote) {
			if (condition) {
				notes.add(successNote);
			} else {
				pass = false;
				notes.add(failureNote);
			}
		}

		private void fail(final String message) {
			pass = false;
			notes.add(message);
		}
	}
}
