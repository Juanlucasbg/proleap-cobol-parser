package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.Assume;
import org.junit.Test;

public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern GOOGLE_LOGIN_PATTERN = Pattern
			.compile("(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s*sesi[oó]n\\s*con\\s*google|google)");
	private static final Pattern MI_NEGOCIO_PATTERN = Pattern.compile("(?i)mi\\s*negocio");
	private static final Pattern AGREGAR_NEGOCIO_PATTERN = Pattern.compile("(?i)agregar\\s*negocio");
	private static final Pattern ADMINISTRAR_NEGOCIOS_PATTERN = Pattern.compile("(?i)administrar\\s*negocios");
	private static final Pattern INFORMACION_GENERAL_PATTERN = Pattern.compile("(?i)informaci[oó]n\\s*general");
	private static final Pattern DETALLES_CUENTA_PATTERN = Pattern.compile("(?i)detalles\\s*de\\s*la\\s*cuenta");
	private static final Pattern TUS_NEGOCIOS_PATTERN = Pattern.compile("(?i)tus\\s*negocios");
	private static final Pattern SECCION_LEGAL_PATTERN = Pattern.compile("(?i)secci[oó]n\\s*legal");
	private static final Pattern TERMINOS_PATTERN = Pattern.compile("(?i)t[ée]rminos\\s*y\\s*condiciones");
	private static final Pattern PRIVACIDAD_PATTERN = Pattern.compile("(?i)pol[ií]tica\\s*de\\s*privacidad");
	private static final Pattern CUPO_NEGOCIOS_PATTERN = Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios");
	private static final Pattern BUSINESS_PLAN_PATTERN = Pattern.compile("(?i)business\\s*plan");
	private static final Pattern CAMBIAR_PLAN_PATTERN = Pattern.compile("(?i)cambiar\\s*plan");
	private static final Pattern CUENTA_CREADA_PATTERN = Pattern.compile("(?i)cuenta\\s*creada");
	private static final Pattern ESTADO_ACTIVO_PATTERN = Pattern.compile("(?i)estado\\s*activo");
	private static final Pattern IDIOMA_SELECCIONADO_PATTERN = Pattern.compile("(?i)idioma\\s*seleccionado");
	private static final int SHORT_TIMEOUT_MS = 8_000;
	private static final int LONG_TIMEOUT_MS = 30_000;

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		Assume.assumeTrue("Enable with -Dsaleads.e2e=true", Boolean.parseBoolean(System.getProperty("saleads.e2e", "false")));

		final String loginUrl = firstNonBlank(System.getProperty("saleads.url"), System.getenv("SALEADS_URL"));
		Assume.assumeTrue("Provide -Dsaleads.url or SALEADS_URL for the login page of the current environment.",
				loginUrl != null && !loginUrl.isBlank());

		final String expectedEmail = firstNonBlank(System.getProperty("saleads.email"), System.getenv("SALEADS_EMAIL"),
				DEFAULT_GOOGLE_EMAIL);
		final String expectedUserName = firstNonBlank(System.getProperty("saleads.userName"), System.getenv("SALEADS_USER_NAME"));
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));
		final Path evidenceDir = buildEvidenceDirectory();

		final Map<String, Boolean> report = initializeReport();
		final List<String> notes = new ArrayList<>();

		String termsFinalUrl = "N/A";
		String privacyFinalUrl = "N/A";

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = launchBrowser(playwright, headless);

			try (BrowserContext context = browser.newContext();
					Page page = context.newPage()) {
				page.setDefaultTimeout(SHORT_TIMEOUT_MS);
				page.navigate(loginUrl);
				waitForUi(page);

				// Step 1: Login with Google
				boolean loginPass = stepLoginWithGoogle(page, context, expectedEmail, evidenceDir, notes);
				report.put("Login", loginPass);

				// Step 2: Open Mi Negocio menu
				boolean menuPass = loginPass && stepOpenMiNegocioMenu(page, evidenceDir, notes);
				if (!loginPass) {
					notes.add("Mi Negocio menu: not executed because login failed.");
				}
				report.put("Mi Negocio menu", menuPass);

				// Step 3: Validate Agregar Negocio modal
				boolean modalPass = menuPass && stepValidateAgregarNegocioModal(page, evidenceDir, notes);
				if (!menuPass) {
					notes.add("Agregar Negocio modal: not executed because Mi Negocio menu step failed.");
				}
				report.put("Agregar Negocio modal", modalPass);

				// Step 4: Open Administrar Negocios
				boolean administrarPass = menuPass && stepOpenAdministrarNegocios(page, evidenceDir, notes);
				if (!menuPass) {
					notes.add("Administrar Negocios view: not executed because Mi Negocio menu step failed.");
				}
				report.put("Administrar Negocios view", administrarPass);

				// Step 5: Validate Informacion General
				boolean infoGeneralPass = administrarPass
						&& stepValidateInformacionGeneral(page, expectedEmail, expectedUserName, notes);
				if (!administrarPass) {
					notes.add("Informacion General: not executed because Administrar Negocios view failed.");
				}
				report.put("Información General", infoGeneralPass);

				// Step 6: Validate Detalles de la Cuenta
				boolean detallesPass = administrarPass && stepValidateDetallesCuenta(page);
				if (!administrarPass) {
					notes.add("Detalles de la Cuenta: not executed because Administrar Negocios view failed.");
				}
				report.put("Detalles de la Cuenta", detallesPass);

				// Step 7: Validate Tus Negocios
				boolean tusNegociosPass = administrarPass && stepValidateTusNegocios(page);
				if (!administrarPass) {
					notes.add("Tus Negocios: not executed because Administrar Negocios view failed.");
				}
				report.put("Tus Negocios", tusNegociosPass);

				// Step 8: Validate Terminos y Condiciones
				boolean terminosPass = administrarPass;
				if (administrarPass) {
					LegalStepResult terminosResult = openAndValidateLegalLink(page, context, TERMINOS_PATTERN, TERMINOS_PATTERN,
							"08-terminos-y-condiciones.png", evidenceDir, notes);
					terminosPass = terminosResult.passed;
					termsFinalUrl = terminosResult.finalUrl;
				} else {
					notes.add("Términos y Condiciones: not executed because Administrar Negocios view failed.");
				}
				report.put("Términos y Condiciones", terminosPass);

				// Step 9: Validate Politica de Privacidad
				boolean privacidadPass = administrarPass;
				if (administrarPass) {
					LegalStepResult privacidadResult = openAndValidateLegalLink(page, context, PRIVACIDAD_PATTERN, PRIVACIDAD_PATTERN,
							"09-politica-de-privacidad.png", evidenceDir, notes);
					privacidadPass = privacidadResult.passed;
					privacyFinalUrl = privacidadResult.finalUrl;
				} else {
					notes.add("Política de Privacidad: not executed because Administrar Negocios view failed.");
				}
				report.put("Política de Privacidad", privacidadPass);
			}
		}

		final String finalReport = buildFinalReport(report, termsFinalUrl, privacyFinalUrl, notes, evidenceDir);
		Files.writeString(evidenceDir.resolve("final-report.txt"), finalReport, StandardCharsets.UTF_8);
		System.out.println(finalReport);
		assertTrue(finalReport, report.values().stream().allMatch(Boolean::booleanValue));
	}

	private Browser launchBrowser(final Playwright playwright, final boolean headless) {
		final String browserName = System.getProperty("saleads.browser", "chromium").trim().toLowerCase(Locale.ROOT);
		final BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(headless);

		switch (browserName) {
		case "firefox":
			return playwright.firefox().launch(options);
		case "webkit":
			return playwright.webkit().launch(options);
		case "chromium":
		default:
			return playwright.chromium().launch(options);
		}
	}

	private boolean stepLoginWithGoogle(final Page page, final BrowserContext context, final String expectedEmail,
			final Path evidenceDir, final List<String> notes) throws IOException {
		Locator loginButton = firstVisible(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GOOGLE_LOGIN_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(GOOGLE_LOGIN_PATTERN)),
				page.getByText(GOOGLE_LOGIN_PATTERN));

		if (loginButton == null) {
			notes.add("Login: Google login button was not found.");
			return false;
		}

		loginButton.click();
		waitForUi(page);
		maybeSelectGoogleAccount(context, expectedEmail, notes);
		waitForUi(page);

		boolean appInterfaceVisible = waitUntilVisible(page, page.locator("main"), LONG_TIMEOUT_MS)
				|| waitUntilVisible(page, page.getByRole(AriaRole.MAIN), LONG_TIMEOUT_MS);
		boolean sidebarVisible = waitUntilVisible(page, page.locator("aside"), LONG_TIMEOUT_MS)
				|| waitUntilVisible(page, page.getByText(Pattern.compile("(?i)negocio")), LONG_TIMEOUT_MS);

		if (appInterfaceVisible && sidebarVisible) {
			takeScreenshot(page, evidenceDir.resolve("01-dashboard-loaded.png"), false);
			return true;
		}

		notes.add("Login: Dashboard main interface or sidebar did not become visible.");
		return false;
	}

	private boolean stepOpenMiNegocioMenu(final Page page, final Path evidenceDir, final List<String> notes) throws IOException {
		Locator negocioSection = firstVisible(page, page.getByText(Pattern.compile("(?i)^\\s*negocio\\s*$")),
				page.getByText(Pattern.compile("(?i)negocio")));
		if (negocioSection == null) {
			notes.add("Mi Negocio menu: Sidebar section 'Negocio' not found.");
			return false;
		}

		Locator miNegocioOption = firstVisible(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)),
				page.getByText(MI_NEGOCIO_PATTERN));

		if (miNegocioOption == null) {
			notes.add("Mi Negocio menu: Option 'Mi Negocio' not found.");
			return false;
		}

		miNegocioOption.click();
		waitForUi(page);

		boolean agregarVisible = waitUntilVisible(page, page.getByText(AGREGAR_NEGOCIO_PATTERN), SHORT_TIMEOUT_MS);
		boolean administrarVisible = waitUntilVisible(page, page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN), SHORT_TIMEOUT_MS);

		if (agregarVisible && administrarVisible) {
			takeScreenshot(page, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), false);
			return true;
		}

		notes.add("Mi Negocio menu: Submenu did not show both 'Agregar Negocio' and 'Administrar Negocios'.");
		return false;
	}

	private boolean stepValidateAgregarNegocioModal(final Page page, final Path evidenceDir, final List<String> notes)
			throws IOException {
		Locator agregarNegocioOption = firstVisible(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
				page.getByText(AGREGAR_NEGOCIO_PATTERN));

		if (agregarNegocioOption == null) {
			notes.add("Agregar Negocio modal: 'Agregar Negocio' option not found.");
			return false;
		}

		agregarNegocioOption.click();
		waitForUi(page);

		boolean titleVisible = waitUntilVisible(page, page.getByText(Pattern.compile("(?i)crear\\s*nuevo\\s*negocio")),
				SHORT_TIMEOUT_MS);
		boolean nombreFieldVisible = waitUntilVisible(page, page.getByLabel(Pattern.compile("(?i)nombre\\s*del\\s*negocio")),
				SHORT_TIMEOUT_MS) || waitUntilVisible(page, page.getByPlaceholder(Pattern.compile("(?i)nombre\\s*del\\s*negocio")),
				SHORT_TIMEOUT_MS);
		boolean cupoVisible = waitUntilVisible(page, page.getByText(CUPO_NEGOCIOS_PATTERN), SHORT_TIMEOUT_MS);
		boolean cancelarVisible = waitUntilVisible(page, page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar"))), SHORT_TIMEOUT_MS);
		boolean crearVisible = waitUntilVisible(page, page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear\\s*negocio"))), SHORT_TIMEOUT_MS);

		boolean modalPass = titleVisible && nombreFieldVisible && cupoVisible && cancelarVisible && crearVisible;
		if (!modalPass) {
			notes.add("Agregar Negocio modal: expected title/field/quota/buttons were not all visible.");
			return false;
		}

		takeScreenshot(page, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);

		Locator nombreField = firstVisible(page, page.getByLabel(Pattern.compile("(?i)nombre\\s*del\\s*negocio")),
				page.getByPlaceholder(Pattern.compile("(?i)nombre\\s*del\\s*negocio")));
		if (nombreField != null) {
			nombreField.click();
			waitForUi(page);
			nombreField.fill("Negocio Prueba Automatizacion");
			waitForUi(page);
		}

		Locator cancelarButton = firstVisible(page, page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar"))), page.getByText(Pattern.compile("(?i)cancelar")));
		if (cancelarButton != null) {
			cancelarButton.click();
			waitForUi(page);
		}

		return true;
	}

	private boolean stepOpenAdministrarNegocios(final Page page, final Path evidenceDir, final List<String> notes)
			throws IOException {
		if (!isVisible(page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN))) {
			Locator miNegocioOption = firstVisible(page,
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)),
					page.getByText(MI_NEGOCIO_PATTERN));
			if (miNegocioOption != null) {
				miNegocioOption.click();
				waitForUi(page);
			}
		}

		Locator administrarOption = firstVisible(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_PATTERN)),
				page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN));

		if (administrarOption == null) {
			notes.add("Administrar Negocios view: 'Administrar Negocios' option not found.");
			return false;
		}

		administrarOption.click();
		waitForUi(page);

		boolean informacionGeneralVisible = waitUntilVisible(page, page.getByText(INFORMACION_GENERAL_PATTERN), LONG_TIMEOUT_MS);
		boolean detallesVisible = waitUntilVisible(page, page.getByText(DETALLES_CUENTA_PATTERN), LONG_TIMEOUT_MS);
		boolean tusNegociosVisible = waitUntilVisible(page, page.getByText(TUS_NEGOCIOS_PATTERN), LONG_TIMEOUT_MS);
		boolean seccionLegalVisible = waitUntilVisible(page, page.getByText(SECCION_LEGAL_PATTERN), LONG_TIMEOUT_MS);

		boolean stepPass = informacionGeneralVisible && detallesVisible && tusNegociosVisible && seccionLegalVisible;
		if (stepPass) {
			takeScreenshot(page, evidenceDir.resolve("04-administrar-negocios-page.png"), true);
			return true;
		}

		notes.add("Administrar Negocios view: one or more required sections were not visible.");
		return false;
	}

	private boolean stepValidateInformacionGeneral(final Page page, final String expectedEmail, final String expectedUserName,
			final List<String> notes) {
		Locator section = findSectionByHeading(page, INFORMACION_GENERAL_PATTERN);
		if (section == null) {
			notes.add("Informacion General: section could not be located.");
			return false;
		}

		boolean userNameVisible = false;
		if (expectedUserName != null && !expectedUserName.isBlank()) {
			userNameVisible = waitUntilVisible(page, section.getByText(Pattern.compile(Pattern.quote(expectedUserName), Pattern.CASE_INSENSITIVE)),
					SHORT_TIMEOUT_MS);
		} else {
			final String sectionText = safeInnerText(section);
			userNameVisible = detectLikelyUserName(sectionText, expectedEmail);
		}

		boolean userEmailVisible = waitUntilVisible(page,
				section.getByText(Pattern.compile(Pattern.quote(expectedEmail), Pattern.CASE_INSENSITIVE)), SHORT_TIMEOUT_MS);
		boolean businessPlanVisible = waitUntilVisible(page, section.getByText(BUSINESS_PLAN_PATTERN), SHORT_TIMEOUT_MS);
		boolean cambiarPlanVisible = waitUntilVisible(page,
				section.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(CAMBIAR_PLAN_PATTERN)), SHORT_TIMEOUT_MS)
				|| waitUntilVisible(page, section.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(CAMBIAR_PLAN_PATTERN)),
						SHORT_TIMEOUT_MS)
				|| waitUntilVisible(page, section.getByText(CAMBIAR_PLAN_PATTERN), SHORT_TIMEOUT_MS);

		if (!userNameVisible) {
			notes.add("Informacion General: user name was not detected.");
		}
		if (!userEmailVisible) {
			notes.add("Informacion General: expected email '" + expectedEmail + "' was not visible.");
		}

		return userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible;
	}

	private boolean stepValidateDetallesCuenta(final Page page) {
		Locator section = findSectionByHeading(page, DETALLES_CUENTA_PATTERN);
		if (section == null) {
			return false;
		}

		boolean cuentaCreadaVisible = waitUntilVisible(page, section.getByText(CUENTA_CREADA_PATTERN), SHORT_TIMEOUT_MS);
		boolean estadoActivoVisible = waitUntilVisible(page, section.getByText(ESTADO_ACTIVO_PATTERN), SHORT_TIMEOUT_MS);
		boolean idiomaVisible = waitUntilVisible(page, section.getByText(IDIOMA_SELECCIONADO_PATTERN), SHORT_TIMEOUT_MS);
		return cuentaCreadaVisible && estadoActivoVisible && idiomaVisible;
	}

	private boolean stepValidateTusNegocios(final Page page) {
		Locator section = findSectionByHeading(page, TUS_NEGOCIOS_PATTERN);
		if (section == null) {
			return false;
		}

		boolean addButtonVisible = waitUntilVisible(page,
				section.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)), SHORT_TIMEOUT_MS)
				|| waitUntilVisible(page, section.getByText(AGREGAR_NEGOCIO_PATTERN), SHORT_TIMEOUT_MS);
		boolean quotaVisible = waitUntilVisible(page, section.getByText(CUPO_NEGOCIOS_PATTERN), SHORT_TIMEOUT_MS);
		boolean listVisible = hasBusinessListLikeContent(section);
		return listVisible && addButtonVisible && quotaVisible;
	}

	private LegalStepResult openAndValidateLegalLink(final Page appPage, final BrowserContext context, final Pattern linkPattern,
			final Pattern headingPattern, final String screenshotFileName, final Path evidenceDir, final List<String> notes)
			throws IOException {
		Locator legalSection = findSectionByHeading(appPage, SECCION_LEGAL_PATTERN);
		Locator link;
		if (legalSection != null) {
			link = firstVisible(appPage, legalSection.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(linkPattern)),
					legalSection.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(linkPattern)),
					legalSection.getByText(linkPattern), appPage.getByText(linkPattern));
		} else {
			link = firstVisible(appPage, appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkPattern)),
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(linkPattern)), appPage.getByText(linkPattern));
		}

		if (link == null) {
			notes.add("Legal step: link '" + linkPattern.pattern() + "' not found.");
			return new LegalStepResult(false, "N/A");
		}

		int beforePageCount = context.pages().size();
		final String appUrlBeforeClick = appPage.url();

		link.click();
		waitForUi(appPage);

		Page legalPage = appPage;
		boolean openedInNewTab = false;
		Page detectedNewTab = waitForNewTab(context, beforePageCount, SHORT_TIMEOUT_MS);
		if (detectedNewTab != null) {
			legalPage = detectedNewTab;
			openedInNewTab = true;
			legalPage.bringToFront();
			waitForUi(legalPage);
		}

		boolean headingVisible = waitUntilVisible(legalPage,
				legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)), SHORT_TIMEOUT_MS)
				|| waitUntilVisible(legalPage, legalPage.getByText(headingPattern), SHORT_TIMEOUT_MS);
		boolean contentVisible = safeInnerText(legalPage.locator("body")).trim().length() > 120;
		String finalUrl = legalPage.url();

		if (headingVisible && contentVisible) {
			takeScreenshot(legalPage, evidenceDir.resolve(screenshotFileName), false);
		} else {
			notes.add("Legal step: heading/content validation failed for '" + headingPattern.pattern() + "'.");
		}

		if (openedInNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else if (!appPage.url().equals(appUrlBeforeClick)) {
			try {
				appPage.goBack();
				waitForUi(appPage);
			} catch (Exception ignored) {
				// If goBack is not available in the current context, continue with remaining checks.
			}
		}

		return new LegalStepResult(headingVisible && contentVisible, finalUrl);
	}

	private Map<String, Boolean> initializeReport() {
		Map<String, Boolean> report = new LinkedHashMap<>();
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);
		return report;
	}

	private String buildFinalReport(final Map<String, Boolean> report, final String termsFinalUrl, final String privacyFinalUrl,
			final List<String> notes, final Path evidenceDir) {
		StringBuilder sb = new StringBuilder();
		sb.append("Test name: ").append(TEST_NAME).append('\n');
		sb.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n');
		sb.append('\n').append("Validation summary").append('\n');
		sb.append("------------------").append('\n');

		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			sb.append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL").append('\n');
		}

		sb.append('\n').append("Final URLs").append('\n');
		sb.append("----------").append('\n');
		sb.append("Términos y Condiciones: ").append(termsFinalUrl).append('\n');
		sb.append("Política de Privacidad: ").append(privacyFinalUrl).append('\n');

		if (!notes.isEmpty()) {
			sb.append('\n').append("Notes").append('\n');
			sb.append("-----").append('\n');
			for (String note : notes) {
				sb.append("- ").append(note).append('\n');
			}
		}

		return sb.toString();
	}

	private Path buildEvidenceDirectory() throws IOException {
		String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		String configuredDir = System.getProperty("saleads.evidenceDir");
		Path evidenceDir = configuredDir == null || configuredDir.isBlank() ? Paths.get("target", "saleads-evidence", timestamp)
				: Paths.get(configuredDir);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private void maybeSelectGoogleAccount(final BrowserContext context, final String email, final List<String> notes) {
		Pattern emailPattern = Pattern.compile(Pattern.quote(email), Pattern.CASE_INSENSITIVE);
		long deadline = System.currentTimeMillis() + SHORT_TIMEOUT_MS;

		while (System.currentTimeMillis() < deadline) {
			for (Page candidatePage : context.pages()) {
				try {
					Locator accountOption = firstVisible(candidatePage, candidatePage.getByText(emailPattern),
							candidatePage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(emailPattern)),
							candidatePage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(emailPattern)));
					if (accountOption != null) {
						accountOption.click();
						waitForUi(candidatePage);
						return;
					}
				} catch (Exception ignored) {
					// Keep scanning all tabs while Google account selector may still be rendering.
				}
			}
			sleep(250);
		}

		notes.add("Login: Google account selector for '" + email + "' was not visible; continuing.");
	}

	private Locator firstVisible(final Page page, final Locator... locators) {
		for (Locator locator : locators) {
			if (locator == null) {
				continue;
			}
			Locator first = locator.first();
			if (waitUntilVisible(page, first, 2_000)) {
				return first;
			}
		}
		return null;
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator.first().isVisible();
		} catch (Exception ignored) {
			return false;
		}
	}

	private boolean waitUntilVisible(final Page page, final Locator locator, final int timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			try {
				if (locator.first().isVisible()) {
					return true;
				}
			} catch (Exception ignored) {
				// keep polling
			}
			page.waitForTimeout(250);
		}
		return false;
	}

	private Locator findSectionByHeading(final Page page, final Pattern headingPattern) {
		Locator heading = page.getByText(headingPattern).first();
		if (!waitUntilVisible(page, heading, SHORT_TIMEOUT_MS)) {
			return null;
		}
		return heading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
	}

	private boolean detectLikelyUserName(final String sectionText, final String email) {
		if (sectionText == null || sectionText.isBlank()) {
			return false;
		}

		String[] lines = sectionText.split("\\R");
		for (String line : lines) {
			String normalized = line.trim();
			if (normalized.isEmpty()) {
				continue;
			}
			String lower = normalized.toLowerCase(Locale.ROOT);
			if (email != null && !email.isBlank() && lower.contains(email.toLowerCase(Locale.ROOT))) {
				continue;
			}
			if (lower.contains("informacion general") || lower.contains("información general")
					|| lower.contains("business plan") || lower.contains("cambiar plan")) {
				continue;
			}
			if (normalized.length() < 4 || normalized.length() > 60) {
				continue;
			}
			if (normalized.contains("@")) {
				continue;
			}
			if (normalized.split("\\s+").length >= 2) {
				return true;
			}
		}

		return false;
	}

	private boolean hasBusinessListLikeContent(final Locator section) {
		try {
			int listLikeItems = section.locator("li, tr, article, [role='listitem'], [role='row']").count();
			if (listLikeItems > 0) {
				return true;
			}

			String text = safeInnerText(section);
			if (text.isBlank()) {
				return false;
			}

			int meaningfulLines = 0;
			for (String line : text.split("\\R")) {
				String normalized = line.trim();
				if (normalized.isEmpty()) {
					continue;
				}
				String lower = normalized.toLowerCase(Locale.ROOT);
				if (lower.contains("tus negocios") || lower.contains("agregar negocio") || lower.contains("tienes 2 de 3 negocios")) {
					continue;
				}
				meaningfulLines++;
				if (meaningfulLines >= 1) {
					return true;
				}
			}
			return false;
		} catch (Exception ignored) {
			return false;
		}
	}

	private Page waitForNewTab(final BrowserContext context, final int pageCountBeforeClick, final int timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (context.pages().size() > pageCountBeforeClick) {
				return context.pages().get(context.pages().size() - 1);
			}
			sleep(250);
		}
		return null;
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (TimeoutError ignored) {
			// Some transitions are client-side only; continue with a short settle delay.
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (TimeoutError ignored) {
			// Some screens keep background polling requests active; do not fail for that.
		}

		page.waitForTimeout(400);
	}

	private void takeScreenshot(final Page page, final Path path, final boolean fullPage) throws IOException {
		Files.createDirectories(path.getParent());
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private String safeInnerText(final Locator locator) {
		try {
			return locator.first().innerText();
		} catch (Exception ignored) {
			return "";
		}
	}

	private String firstNonBlank(final String... candidates) {
		for (String candidate : candidates) {
			if (candidate != null && !candidate.isBlank()) {
				return candidate.trim();
			}
		}
		return null;
	}

	private void sleep(final long milliseconds) {
		try {
			Thread.sleep(milliseconds);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private static class LegalStepResult {
		private final boolean passed;
		private final String finalUrl;

		private LegalStepResult(final boolean passed, final String finalUrl) {
			this.passed = passed;
			this.finalUrl = finalUrl;
		}
	}
}
