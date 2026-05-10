package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Browser.NewContextOptions;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Locator.WaitForOptions;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleAdsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern GOOGLE_BUTTON_PATTERN = Pattern.compile(
			"(?i)(google|sign in|sign-in|iniciar|continuar)");
	private static final Pattern NEGOCIO_PATTERN = Pattern.compile("(?i)Negocio");
	private static final Pattern MI_NEGOCIO_PATTERN = Pattern.compile("(?i)Mi\\s+Negocio");
	private static final Pattern AGREGAR_NEGOCIO_PATTERN = Pattern.compile("(?i)Agregar\\s+Negocio");
	private static final Pattern ADMINISTRAR_NEGOCIOS_PATTERN = Pattern
			.compile("(?i)Administrar\\s+Negocios");
	private static final Pattern CREAR_NUEVO_NEGOCIO_PATTERN = Pattern
			.compile("(?i)Crear\\s+Nuevo\\s+Negocio");
	private static final String NOMBRE_NEGOCIO_TEXT = "Nombre del Negocio";
	private static final Pattern NOMBRE_NEGOCIO_PATTERN = Pattern.compile("(?i)Nombre\\s+del\\s+Negocio");
	private static final Pattern DOS_DE_TRES_PATTERN = Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios");
	private static final Pattern INFORMACION_GENERAL_PATTERN = Pattern
			.compile("(?i)Informaci[o\\u00F3]n\\s+General");
	private static final Pattern DETALLES_CUENTA_PATTERN = Pattern
			.compile("(?i)Detalles\\s+de\\s+la\\s+Cuenta");
	private static final Pattern TUS_NEGOCIOS_PATTERN = Pattern.compile("(?i)Tus\\s+Negocios");
	private static final Pattern SECCION_LEGAL_PATTERN = Pattern.compile("(?i)Secci[o\\u00F3]n\\s+Legal");
	private static final Pattern TERMINOS_PATTERN = Pattern
			.compile("(?i)T[\\u00E9e]rminos\\s+y\\s+Condiciones");
	private static final Pattern PRIVACIDAD_PATTERN = Pattern
			.compile("(?i)Pol[i\\u00ED]tica\\s+de\\s+Privacidad");
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
	private static final double DEFAULT_TIMEOUT_MS = 15_000;
	private static final double LONG_TIMEOUT_MS = 45_000;

	@Test
	public void saleadsMiNegocioWorkflow() throws Exception {
		final Map<String, StepStatus> report = initializeReport();
		final String runId = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC)
				.format(Instant.now());
		final Path evidenceDir = Paths.get("target", "saleads-evidence", TEST_NAME, runId);
		Files.createDirectories(evidenceDir);

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new Browser.LaunchOptions().setHeadless(readHeadlessFlag()));
			final BrowserContext context = browser.newContext(new NewContextOptions().setViewportSize(1600, 1000));
			final Page page = context.newPage();

			openLoginPageIfConfigured(page);
			final boolean loginPassed = validateLoginWithGoogle(page, evidenceDir, report);

			if (loginPassed) {
				validateMiNegocioMenu(page, evidenceDir, report);
				validateAgregarNegocioModal(page, evidenceDir, report);
				validateAdministrarNegociosView(page, evidenceDir, report);
				validateInformacionGeneral(page, report);
				validateDetallesCuenta(page, report);
				validateTusNegocios(page, report);
				validateLegalDocument(page, evidenceDir, report, "T\u00E9rminos y Condiciones", TERMINOS_PATTERN,
						"T\u00E9rminos y Condiciones");
				validateLegalDocument(page, evidenceDir, report, "Pol\u00EDtica de Privacidad", PRIVACIDAD_PATTERN,
						"Pol\u00EDtica de Privacidad");
			} else {
				markRemainingStepsBlocked(report, "Blocked because login did not complete.");
			}
		}

		final Path reportPath = evidenceDir.resolve("final_report.json");
		writeReport(report, reportPath);
		assertTrue("At least one validation failed. Check: " + reportPath, allPassed(report));
	}

	private void openLoginPageIfConfigured(final Page page) {
		final Optional<String> url = firstNonBlank(System.getenv("SALEADS_LOGIN_URL"),
				System.getProperty("saleads.login.url"), System.getenv("SALEADS_BASE_URL"),
				System.getProperty("saleads.base.url"));
		if (url.isPresent()) {
			page.navigate(url.get());
		}
		waitForUi(page);
	}

	private boolean validateLoginWithGoogle(final Page page, final Path evidenceDir,
			final Map<String, StepStatus> report) {
		try {
			final Locator googleButton = firstVisible(page,
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GOOGLE_BUTTON_PATTERN)),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(GOOGLE_BUTTON_PATTERN)),
					page.getByText(Pattern.compile("(?i)(sign in|iniciar|continuar).{0,20}google")));

			if (googleButton == null) {
				report.put("Login",
						StepStatus.fail("Could not find login button / 'Sign in with Google' on current page."));
				takeScreenshot(page, evidenceDir, "01_login_button_not_found.png", true);
				return false;
			}

			Page popup = null;
			try {
				popup = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(8_000),
						() -> clickAndWait(page, googleButton));
			} catch (PlaywrightException ignored) {
				clickAndWait(page, googleButton);
			}

			if (popup != null) {
				waitForUi(popup);
				selectGoogleAccountIfVisible(popup);
				try {
					popup.waitForClose(new Page.WaitForCloseOptions().setTimeout(30_000));
				} catch (PlaywrightException ignored) {
					// Popup may stay open in some flows; app page validation below determines success.
				}
			} else {
				selectGoogleAccountIfVisible(page);
			}

			waitForUi(page);
			final boolean sidebarVisible = waitForVisible(page, page.locator("aside"), LONG_TIMEOUT_MS)
					|| waitForVisible(page, page.getByText(NEGOCIO_PATTERN), LONG_TIMEOUT_MS);
			final boolean mainVisible = waitForVisible(page, page.getByRole(AriaRole.MAIN), 8_000) || sidebarVisible;

			if (mainVisible && sidebarVisible) {
				takeScreenshot(page, evidenceDir, "01_dashboard_loaded.png", true);
				report.put("Login", StepStatus.pass("Main interface and left sidebar are visible."));
				return true;
			}

			takeScreenshot(page, evidenceDir, "01_login_validation_failed.png", true);
			report.put("Login", StepStatus.fail("Login validation failed: dashboard/sidebar not detected."));
			return false;
		} catch (Exception ex) {
			report.put("Login", StepStatus.fail("Unexpected login error: " + ex.getMessage()));
			return false;
		}
	}

	private void validateMiNegocioMenu(final Page page, final Path evidenceDir, final Map<String, StepStatus> report) {
		try {
			final Locator negocioSection = firstVisible(page, page.getByText(NEGOCIO_PATTERN),
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEGOCIO_PATTERN)));
			if (negocioSection != null) {
				clickAndWait(page, negocioSection);
			}

			final Locator miNegocio = firstVisible(page,
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)),
					page.getByText(MI_NEGOCIO_PATTERN));
			if (miNegocio == null) {
				report.put("Mi Negocio menu", StepStatus.fail("Could not find 'Mi Negocio' in left sidebar."));
				return;
			}
			clickAndWait(page, miNegocio);

			final boolean agregarVisible = waitForVisible(page, page.getByText(AGREGAR_NEGOCIO_PATTERN), DEFAULT_TIMEOUT_MS);
			final boolean administrarVisible = waitForVisible(page, page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN),
					DEFAULT_TIMEOUT_MS);

			takeScreenshot(page, evidenceDir, "02_mi_negocio_menu_expanded.png", true);
			if (agregarVisible && administrarVisible) {
				report.put("Mi Negocio menu", StepStatus.pass("Submenu expanded and expected options are visible."));
			} else {
				report.put("Mi Negocio menu", StepStatus
						.fail("Mi Negocio submenu did not expose both 'Agregar Negocio' and 'Administrar Negocios'."));
			}
		} catch (Exception ex) {
			report.put("Mi Negocio menu", StepStatus.fail("Unexpected error: " + ex.getMessage()));
		}
	}

	private void validateAgregarNegocioModal(final Page page, final Path evidenceDir,
			final Map<String, StepStatus> report) {
		try {
			final Locator agregarNegocio = firstVisible(page,
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
					page.getByText(AGREGAR_NEGOCIO_PATTERN));
			if (agregarNegocio == null) {
				report.put("Agregar Negocio modal", StepStatus.fail("Could not click 'Agregar Negocio'."));
				return;
			}
			clickAndWait(page, agregarNegocio);

			final boolean titleVisible = waitForVisible(page, page.getByText(CREAR_NUEVO_NEGOCIO_PATTERN), DEFAULT_TIMEOUT_MS);
			final boolean nameInputVisible = waitForVisible(page, page.getByLabel(NOMBRE_NEGOCIO_TEXT), 5_000)
					|| waitForVisible(page, page.locator("input[placeholder*='Negocio']"), 5_000)
					|| waitForVisible(page, page.getByText(NOMBRE_NEGOCIO_PATTERN), 5_000);
			final boolean quotaVisible = waitForVisible(page, page.getByText(DOS_DE_TRES_PATTERN), DEFAULT_TIMEOUT_MS);
			final boolean cancelarVisible = waitForVisible(page,
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Cancelar"))),
					DEFAULT_TIMEOUT_MS);
			final boolean crearVisible = waitForVisible(page,
					page.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Crear\\s+Negocio"))),
					DEFAULT_TIMEOUT_MS);

			if (nameInputVisible) {
				final Locator input = firstVisible(page, page.getByLabel(NOMBRE_NEGOCIO_TEXT),
						page.locator("input[placeholder*='Negocio']"));
				if (input != null) {
					input.fill("Negocio Prueba Automatizacion");
				}
			}

			final Locator cancelar = firstVisible(page,
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Cancelar"))),
					page.getByText(Pattern.compile("(?i)Cancelar")));
			if (cancelar != null) {
				clickAndWait(page, cancelar);
			}

			takeScreenshot(page, evidenceDir, "03_agregar_negocio_modal.png", true);
			if (titleVisible && nameInputVisible && quotaVisible && cancelarVisible && crearVisible) {
				report.put("Agregar Negocio modal",
						StepStatus.pass("Modal validated: title, input, quota text, and action buttons are present."));
			} else {
				report.put("Agregar Negocio modal",
						StepStatus.fail("Modal validation failed for one or more required elements."));
			}
		} catch (Exception ex) {
			report.put("Agregar Negocio modal", StepStatus.fail("Unexpected error: " + ex.getMessage()));
		}
	}

	private void validateAdministrarNegociosView(final Page page, final Path evidenceDir,
			final Map<String, StepStatus> report) {
		try {
			final Locator administrar = firstVisible(page,
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_PATTERN)),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_PATTERN)),
					page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN));
			if (administrar == null) {
				final Locator miNegocio = firstVisible(page, page.getByText(MI_NEGOCIO_PATTERN));
				if (miNegocio != null) {
					clickAndWait(page, miNegocio);
				}
			}

			final Locator administrarAfterExpand = firstVisible(page,
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_PATTERN)),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_PATTERN)),
					page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN));

			if (administrarAfterExpand == null) {
				report.put("Administrar Negocios view", StepStatus.fail("Could not find 'Administrar Negocios'."));
				return;
			}
			clickAndWait(page, administrarAfterExpand);
			waitForUi(page);

			final boolean informacionGeneralVisible = waitForVisible(page, page.getByText(INFORMACION_GENERAL_PATTERN),
					DEFAULT_TIMEOUT_MS);
			final boolean detallesCuentaVisible = waitForVisible(page, page.getByText(DETALLES_CUENTA_PATTERN),
					DEFAULT_TIMEOUT_MS);
			final boolean tusNegociosVisible = waitForVisible(page, page.getByText(TUS_NEGOCIOS_PATTERN), DEFAULT_TIMEOUT_MS);
			final boolean seccionLegalVisible = waitForVisible(page, page.getByText(SECCION_LEGAL_PATTERN), DEFAULT_TIMEOUT_MS);

			takeScreenshot(page, evidenceDir, "04_administrar_negocios_view.png", true);
			if (informacionGeneralVisible && detallesCuentaVisible && tusNegociosVisible && seccionLegalVisible) {
				report.put("Administrar Negocios view",
						StepStatus.pass("All expected sections are present in the account page."));
			} else {
				report.put("Administrar Negocios view",
						StepStatus.fail("Missing one or more account sections in Administrar Negocios page."));
			}
		} catch (Exception ex) {
			report.put("Administrar Negocios view", StepStatus.fail("Unexpected error: " + ex.getMessage()));
		}
	}

	private void validateInformacionGeneral(final Page page, final Map<String, StepStatus> report) {
		try {
			final String bodyText = getBodyText(page);
			final boolean userNameVisible = containsPattern(bodyText,
					Pattern.compile("(?i)(juan|lucas|barbier|garzon|nombre\\s+de\\s+usuario|nombre)"));
			final boolean userEmailVisible = containsPattern(bodyText, Pattern.compile(Pattern.quote(GOOGLE_ACCOUNT_EMAIL), Pattern.CASE_INSENSITIVE))
					|| containsPattern(bodyText, EMAIL_PATTERN);
			final boolean businessPlanVisible = containsPattern(bodyText, Pattern.compile("(?i)BUSINESS\\s+PLAN"));
			final boolean cambiarPlanVisible = waitForVisible(page,
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Cambiar\\s+Plan"))),
					DEFAULT_TIMEOUT_MS)
					|| containsPattern(bodyText, Pattern.compile("(?i)Cambiar\\s+Plan"));

			if (userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible) {
				report.put("Informaci\u00F3n General", StepStatus.pass("Informacion General content validated."));
			} else {
				report.put("Informaci\u00F3n General", StepStatus
						.fail("Informacion General validation failed (name/email/plan/button checks)."));
			}
		} catch (Exception ex) {
			report.put("Informaci\u00F3n General", StepStatus.fail("Unexpected error: " + ex.getMessage()));
		}
	}

	private void validateDetallesCuenta(final Page page, final Map<String, StepStatus> report) {
		try {
			final String bodyText = getBodyText(page);
			final boolean cuentaCreadaVisible = containsPattern(bodyText, Pattern.compile("(?i)Cuenta\\s+creada"));
			final boolean estadoActivoVisible = containsPattern(bodyText, Pattern.compile("(?i)Estado\\s+activo"));
			final boolean idiomaVisible = containsPattern(bodyText,
					Pattern.compile("(?i)Idioma\\s+seleccionado|Idioma"));

			if (cuentaCreadaVisible && estadoActivoVisible && idiomaVisible) {
				report.put("Detalles de la Cuenta", StepStatus.pass("Detalles de la Cuenta validated."));
			} else {
				report.put("Detalles de la Cuenta",
						StepStatus.fail("Missing one or more required details in account details section."));
			}
		} catch (Exception ex) {
			report.put("Detalles de la Cuenta", StepStatus.fail("Unexpected error: " + ex.getMessage()));
		}
	}

	private void validateTusNegocios(final Page page, final Map<String, StepStatus> report) {
		try {
			final String bodyText = getBodyText(page);
			final boolean headingVisible = containsPattern(bodyText, TUS_NEGOCIOS_PATTERN);
			final boolean addBusinessButtonVisible = waitForVisible(page,
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)), 5_000)
					|| containsPattern(bodyText, AGREGAR_NEGOCIO_PATTERN);
			final boolean quotaVisible = containsPattern(bodyText, DOS_DE_TRES_PATTERN);
			final boolean businessListVisible = page.locator("table, ul, [role='list'], [role='row'], article, li")
					.count() > 0;

			if (headingVisible && addBusinessButtonVisible && quotaVisible && businessListVisible) {
				report.put("Tus Negocios", StepStatus.pass("Tus Negocios list and controls validated."));
			} else {
				report.put("Tus Negocios",
						StepStatus.fail("Tus Negocios validation failed (list/button/quota/heading checks)."));
			}
		} catch (Exception ex) {
			report.put("Tus Negocios", StepStatus.fail("Unexpected error: " + ex.getMessage()));
		}
	}

	private void validateLegalDocument(final Page page, final Path evidenceDir, final Map<String, StepStatus> report,
			final String reportFieldName, final Pattern linkPattern, final String screenshotBaseName) {
		try {
			final Locator link = firstVisible(page,
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkPattern)),
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(linkPattern)),
					page.getByText(linkPattern));
			if (link == null) {
				report.put(reportFieldName, StepStatus.fail("Could not find legal link/button: " + reportFieldName));
				return;
			}

			final String appUrlBefore = page.url();
			Page legalPage = null;
			try {
				legalPage = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(8_000),
						() -> clickAndWait(page, link));
			} catch (PlaywrightException ignored) {
				clickAndWait(page, link);
			}

			final Page target = legalPage == null ? page : legalPage;
			waitForUi(target);

			final boolean headingVisible = waitForVisible(target, target.getByRole(AriaRole.HEADING,
					new Page.GetByRoleOptions().setName(linkPattern)), DEFAULT_TIMEOUT_MS)
					|| waitForVisible(target, target.getByText(linkPattern), DEFAULT_TIMEOUT_MS);
			final String legalText = getBodyText(target);
			final boolean legalContentVisible = legalText != null && legalText.trim().length() > 200;

			final String screenshotName = screenshotBaseName.toLowerCase().replace(' ', '_').replace('/', '_')
					+ ".png";
			takeScreenshot(target, evidenceDir, screenshotName, true);

			final String finalUrl = target.url();
			if (headingVisible && legalContentVisible) {
				report.put(reportFieldName,
						StepStatus.pass("Validated legal page. URL: " + finalUrl));
			} else {
				report.put(reportFieldName, StepStatus.fail(
						"Legal page validation failed (heading/content). URL: " + finalUrl));
			}

			if (legalPage != null) {
				legalPage.close();
				page.bringToFront();
			} else if (!page.url().equals(appUrlBefore)) {
				page.goBack();
				waitForUi(page);
			}
		} catch (Exception ex) {
			report.put(reportFieldName, StepStatus.fail("Unexpected error: " + ex.getMessage()));
		}
	}

	private void selectGoogleAccountIfVisible(final Page targetPage) {
		try {
			final Locator account = firstVisible(targetPage, targetPage.getByText(Pattern.compile(Pattern.quote(GOOGLE_ACCOUNT_EMAIL))));
			if (account != null) {
				clickAndWait(targetPage, account);
			}
		} catch (Exception ignored) {
			// Account picker does not always appear if there is an active Google session.
		}
	}

	private static Map<String, StepStatus> initializeReport() {
		final Map<String, StepStatus> report = new LinkedHashMap<>();
		report.put("Login", StepStatus.fail("Not executed."));
		report.put("Mi Negocio menu", StepStatus.fail("Not executed."));
		report.put("Agregar Negocio modal", StepStatus.fail("Not executed."));
		report.put("Administrar Negocios view", StepStatus.fail("Not executed."));
		report.put("Informaci\u00F3n General", StepStatus.fail("Not executed."));
		report.put("Detalles de la Cuenta", StepStatus.fail("Not executed."));
		report.put("Tus Negocios", StepStatus.fail("Not executed."));
		report.put("T\u00E9rminos y Condiciones", StepStatus.fail("Not executed."));
		report.put("Pol\u00EDtica de Privacidad", StepStatus.fail("Not executed."));
		return report;
	}

	private static void markRemainingStepsBlocked(final Map<String, StepStatus> report, final String reason) {
		report.entrySet().stream().filter(entry -> "Login".equals(entry.getKey()) == false)
				.forEach(entry -> entry.setValue(StepStatus.fail(reason)));
	}

	private static boolean allPassed(final Map<String, StepStatus> report) {
		return report.values().stream().allMatch(result -> result.passed);
	}

	private static void writeReport(final Map<String, StepStatus> report, final Path reportPath) throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("{\n");
		builder.append("  \"test\": \"").append(TEST_NAME).append("\",\n");
		builder.append("  \"generatedAt\": \"").append(Instant.now().toString()).append("\",\n");
		builder.append("  \"results\": {\n");

		int index = 0;
		for (Map.Entry<String, StepStatus> entry : report.entrySet()) {
			builder.append("    \"").append(escapeJson(entry.getKey())).append("\": {\n");
			builder.append("      \"status\": \"").append(entry.getValue().passed ? "PASS" : "FAIL").append("\",\n");
			builder.append("      \"detail\": \"").append(escapeJson(entry.getValue().detail)).append("\"\n");
			builder.append("    }");
			index++;
			if (index < report.size()) {
				builder.append(",");
			}
			builder.append("\n");
		}

		builder.append("  }\n");
		builder.append("}\n");

		Files.writeString(reportPath, builder.toString(), StandardCharsets.UTF_8);
	}

	private static String escapeJson(final String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}

	private static Optional<String> firstNonBlank(final String... candidates) {
		for (String candidate : candidates) {
			if (candidate != null && candidate.isBlank() == false) {
				return Optional.of(candidate);
			}
		}
		return Optional.empty();
	}

	private static boolean readHeadlessFlag() {
		final Optional<String> value = firstNonBlank(System.getenv("SALEADS_HEADLESS"),
				System.getProperty("saleads.headless"));
		return value.map(Boolean::parseBoolean).orElse(Boolean.TRUE);
	}

	private static void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10_000));
		} catch (PlaywrightException ignored) {
			// DOM load event may already be completed, or site may keep active connections.
		}
		page.waitForTimeout(650);
	}

	private static void clickAndWait(final Page page, final Locator locator) {
		locator.first().click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUi(page);
	}

	private static boolean waitForVisible(final Page page, final Locator locator, final double timeoutMs) {
		try {
			final WaitForOptions options = new WaitForOptions().setTimeout(timeoutMs).setState(WaitForSelectorState.VISIBLE);
			locator.first().waitFor(options);
			return true;
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private static Locator firstVisible(final Page page, final Locator... locators) {
		for (Locator locator : locators) {
			if (locator != null && waitForVisible(page, locator, 4_000)) {
				return locator.first();
			}
		}
		return null;
	}

	private static void takeScreenshot(final Page page, final Path evidenceDir, final String fileName,
			final boolean fullPage) {
		final Path path = evidenceDir.resolve(fileName);
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private static String getBodyText(final Page page) {
		try {
			return page.locator("body").innerText();
		} catch (PlaywrightException ex) {
			return "";
		}
	}

	private static boolean containsPattern(final String text, final Pattern pattern) {
		return text != null && pattern.matcher(text).find();
	}

	private static final class StepStatus {
		private final boolean passed;
		private final String detail;

		private StepStatus(final boolean passed, final String detail) {
			this.passed = passed;
			this.detail = detail;
		}

		private static StepStatus pass(final String detail) {
			return new StepStatus(true, detail);
		}

		private static StepStatus fail(final String detail) {
			return new StepStatus(false, detail);
		}
	}
}
