package io.proleap.cobol.e2e.saleads;

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
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

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

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern GOOGLE_LOGIN_PATTERN = Pattern
			.compile("(?i)(sign in with google|iniciar sesi[oó]n con google|continuar con google|google)");
	private static final Pattern NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*negocio\\s*$");
	private static final Pattern MI_NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*mi\\s+negocio\\s*$");
	private static final Pattern AGREGAR_NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*agregar\\s+negocio\\s*$");
	private static final Pattern ADMINISTRAR_NEGOCIOS_PATTERN = Pattern
			.compile("(?i)^\\s*administrar\\s+negocios\\s*$");
	private static final Pattern CREAR_NUEVO_NEGOCIO_PATTERN = Pattern
			.compile("(?i)^\\s*crear\\s+nuevo\\s+negocio\\s*$");
	private static final Pattern NOMBRE_NEGOCIO_PATTERN = Pattern.compile("(?i)nombre\\s+del\\s+negocio");
	private static final Pattern NEGOCIOS_LIMIT_PATTERN = Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios");
	private static final Pattern INFORMACION_GENERAL_PATTERN = Pattern.compile("(?i)informaci[oó]n\\s+general");
	private static final Pattern DETALLES_CUENTA_PATTERN = Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta");
	private static final Pattern TUS_NEGOCIOS_PATTERN = Pattern.compile("(?i)tus\\s+negocios");
	private static final Pattern SECCION_LEGAL_PATTERN = Pattern.compile("(?i)secci[oó]n\\s+legal");
	private static final Pattern TERMINOS_PATTERN = Pattern.compile("(?i)t[eé]rminos\\s+y\\s+condiciones");
	private static final Pattern PRIVACIDAD_PATTERN = Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad");
	private static final Pattern BUSINESS_PLAN_PATTERN = Pattern.compile("(?i)business\\s+plan");
	private static final Pattern CAMBIAR_PLAN_PATTERN = Pattern.compile("(?i)cambiar\\s+plan");
	private static final Pattern CUENTA_CREADA_PATTERN = Pattern.compile("(?i)cuenta\\s+creada");
	private static final Pattern ESTADO_ACTIVO_PATTERN = Pattern.compile("(?i)estado\\s+activo");
	private static final Pattern IDIOMA_SELECCIONADO_PATTERN = Pattern.compile("(?i)idioma\\s+seleccionado");
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("(?i)[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}");

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	@Test
	public void saleadsMiNegocioFullWorkflowTest() throws Exception {
		final String loginUrl = getRequiredEnv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to the active SaleADS login page.", loginUrl != null);

		final Path runDir = createRunDirectory();
		final WorkflowReport report = new WorkflowReport(runDir.resolve("saleads_mi_negocio_full_test_report.txt"));

		try (final Playwright playwright = Playwright.create()) {
			final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
					.setHeadless(Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true")));
			final Browser browser = playwright.chromium().launch(launchOptions);
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
			final Page page = context.newPage();

			page.navigate(loginUrl);
			waitForUiLoad(page);

			final boolean loginOk = stepLoginWithGoogle(page, report, runDir);
			final boolean menuOk = loginOk ? stepOpenMiNegocioMenu(page, report, runDir)
					: report.markBlocked("Mi Negocio menu", "Blocked because Login failed.");
			final boolean modalOk = menuOk ? stepValidateAgregarNegocioModal(page, report, runDir)
					: report.markBlocked("Agregar Negocio modal", "Blocked because Mi Negocio menu failed.");
			final boolean administrarOk = menuOk ? stepOpenAdministrarNegocios(page, report, runDir)
					: report.markBlocked("Administrar Negocios view", "Blocked because Mi Negocio menu failed.");

			if (administrarOk) {
				stepValidateInformacionGeneral(page, report);
				stepValidateDetallesCuenta(page, report);
				stepValidateTusNegocios(page, report);
				stepValidateTerminos(page, report, runDir);
				stepValidatePoliticaPrivacidad(page, report, runDir);
			} else {
				report.markBlocked("Información General", "Blocked because Administrar Negocios view failed.");
				report.markBlocked("Detalles de la Cuenta", "Blocked because Administrar Negocios view failed.");
				report.markBlocked("Tus Negocios", "Blocked because Administrar Negocios view failed.");
				report.markBlocked("Términos y Condiciones", "Blocked because Administrar Negocios view failed.");
				report.markBlocked("Política de Privacidad", "Blocked because Administrar Negocios view failed.");
			}

			report.write();
			Assert.assertTrue(report.toString(), report.allPassed());
		}
	}

	private boolean stepLoginWithGoogle(final Page page, final WorkflowReport report, final Path runDir) {
		try {
			final boolean alreadyLoggedIn = waitForVisible(page.locator("aside"), 8000)
					&& waitForVisible(page.getByText(MI_NEGOCIO_PATTERN), 8000);
			if (!alreadyLoggedIn) {
				final Locator loginButton = firstVisible(6000,
						page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GOOGLE_LOGIN_PATTERN)),
						page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(GOOGLE_LOGIN_PATTERN)),
						page.getByText(GOOGLE_LOGIN_PATTERN));

				if (loginButton == null) {
					return report.markFailed("Login",
							"Could not locate a Google login button using visible text selectors.");
				}

				final Page authPage = clickAndCaptureNewPage(page, loginButton, 7000);
				final Page activeAuthPage = authPage == null ? page : authPage;
				selectGoogleAccountIfVisible(activeAuthPage);

				waitForMainApp(page, 30000);
			}

			final boolean interfaceVisible = waitForVisible(page.locator("aside"), 20000);
			final boolean sidebarVisible = waitForVisible(page.getByText(NEGOCIO_PATTERN), 15000)
					|| waitForVisible(page.getByText(MI_NEGOCIO_PATTERN), 15000);
			final boolean pass = interfaceVisible && sidebarVisible;
			takeScreenshot(page, runDir.resolve("01-dashboard-loaded.png"), false);

			if (pass) {
				return report.markPassed("Login", "Main app interface and left sidebar are visible.");
			}
			return report.markFailed("Login", "Could not confirm dashboard interface and left sidebar after login.");
		} catch (final RuntimeException ex) {
			return report.markFailed("Login", "Unexpected error during login step: " + ex.getMessage());
		}
	}

	private boolean stepOpenMiNegocioMenu(final Page page, final WorkflowReport report, final Path runDir) {
		try {
			final Locator sidebar = page.locator("aside");
			if (!waitForVisible(sidebar, 15000)) {
				return report.markFailed("Mi Negocio menu", "Left sidebar is not visible.");
			}

			final Locator negocio = firstVisible(8000,
					sidebar.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(NEGOCIO_PATTERN)),
					sidebar.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(NEGOCIO_PATTERN)),
					sidebar.getByText(NEGOCIO_PATTERN), page.getByText(NEGOCIO_PATTERN));
			if (negocio == null) {
				return report.markFailed("Mi Negocio menu", "Could not find sidebar section 'Negocio'.");
			}

			clickAndWait(page, negocio);

			final Locator miNegocio = firstVisible(8000,
					sidebar.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)),
					sidebar.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)),
					sidebar.getByText(MI_NEGOCIO_PATTERN), page.getByText(MI_NEGOCIO_PATTERN));
			if (miNegocio == null) {
				return report.markFailed("Mi Negocio menu", "Could not find 'Mi Negocio' option.");
			}
			clickAndWait(page, miNegocio);

			final boolean agregarVisible = waitForVisible(page.getByText(AGREGAR_NEGOCIO_PATTERN), 10000);
			final boolean administrarVisible = waitForVisible(page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN), 10000);
			takeScreenshot(page, runDir.resolve("02-mi-negocio-menu-expanded.png"), false);

			final boolean pass = agregarVisible && administrarVisible;
			if (pass) {
				return report.markPassed("Mi Negocio menu",
						"'Mi Negocio' submenu expanded with 'Agregar Negocio' and 'Administrar Negocios'.");
			}
			return report.markFailed("Mi Negocio menu",
					"Submenu did not show both required options ('Agregar Negocio' and 'Administrar Negocios').");
		} catch (final RuntimeException ex) {
			return report.markFailed("Mi Negocio menu", "Unexpected error in menu step: " + ex.getMessage());
		}
	}

	private boolean stepValidateAgregarNegocioModal(final Page page, final WorkflowReport report, final Path runDir) {
		try {
			final Locator agregarNegocio = firstVisible(6000,
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
					page.getByText(AGREGAR_NEGOCIO_PATTERN));
			if (agregarNegocio == null) {
				return report.markFailed("Agregar Negocio modal", "Could not click 'Agregar Negocio'.");
			}
			clickAndWait(page, agregarNegocio);

			final boolean titleVisible = waitForVisible(page.getByText(CREAR_NUEVO_NEGOCIO_PATTERN), 12000);
			final Locator businessNameInput = firstVisible(8000, page.getByLabel(NOMBRE_NEGOCIO_PATTERN),
					page.getByPlaceholder("Nombre del Negocio"), page.getByPlaceholder("Ingresa el nombre del negocio"));
			final boolean inputVisible = businessNameInput != null;
			final boolean limitTextVisible = waitForVisible(page.getByText(NEGOCIOS_LIMIT_PATTERN), 5000);
			final boolean cancelVisible = waitForVisible(
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")), 5000);
			final boolean createVisible = waitForVisible(
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio")), 5000);

			takeScreenshot(page, runDir.resolve("03-agregar-negocio-modal.png"), false);

			if (businessNameInput != null) {
				businessNameInput.first().fill("Negocio Prueba Automatizacion");
				waitForUiLoad(page);
			}

			final Locator cancelButton = firstVisible(3000,
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")));
			if (cancelButton != null) {
				clickAndWait(page, cancelButton);
			}

			final boolean pass = titleVisible && inputVisible && limitTextVisible && cancelVisible && createVisible;
			if (pass) {
				return report.markPassed("Agregar Negocio modal", "Modal structure and actions validated successfully.");
			}
			return report.markFailed("Agregar Negocio modal",
					"One or more modal validations failed (title/input/limit text/buttons).");
		} catch (final RuntimeException ex) {
			return report.markFailed("Agregar Negocio modal", "Unexpected error in modal step: " + ex.getMessage());
		}
	}

	private boolean stepOpenAdministrarNegocios(final Page page, final WorkflowReport report, final Path runDir) {
		try {
			Locator administrarNegocios = firstVisible(4000, page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_PATTERN)),
					page.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_PATTERN)));

			if (administrarNegocios == null) {
				final Locator miNegocio = firstVisible(4000, page.getByText(MI_NEGOCIO_PATTERN),
						page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)));
				if (miNegocio != null) {
					clickAndWait(page, miNegocio);
				}
				administrarNegocios = firstVisible(6000, page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN),
						page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_PATTERN)),
						page.getByRole(AriaRole.BUTTON,
								new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_PATTERN)));
			}

			if (administrarNegocios == null) {
				return report.markFailed("Administrar Negocios view", "Could not locate 'Administrar Negocios'.");
			}

			clickAndWait(page, administrarNegocios);
			waitForUiLoad(page);

			final boolean infoGeneral = waitForVisible(page.getByText(INFORMACION_GENERAL_PATTERN), 12000);
			final boolean detallesCuenta = waitForVisible(page.getByText(DETALLES_CUENTA_PATTERN), 12000);
			final boolean tusNegocios = waitForVisible(page.getByText(TUS_NEGOCIOS_PATTERN), 12000);
			final boolean seccionLegal = waitForVisible(page.getByText(SECCION_LEGAL_PATTERN), 12000);
			takeScreenshot(page, runDir.resolve("04-administrar-negocios-page.png"), true);

			final boolean pass = infoGeneral && detallesCuenta && tusNegocios && seccionLegal;
			if (pass) {
				return report.markPassed("Administrar Negocios view",
						"All required account sections are visible on the page.");
			}
			return report.markFailed("Administrar Negocios view",
					"Missing one or more required sections (Información General, Detalles de la Cuenta, Tus Negocios, Sección Legal).");
		} catch (final RuntimeException ex) {
			return report.markFailed("Administrar Negocios view", "Unexpected error in account page step: " + ex.getMessage());
		}
	}

	private boolean stepValidateInformacionGeneral(final Page page, final WorkflowReport report) {
		try {
			final Locator sectionHeader = page.getByText(INFORMACION_GENERAL_PATTERN);
			final boolean sectionVisible = waitForVisible(sectionHeader, 8000);
			final boolean emailVisible = waitForVisible(page.getByText(EMAIL_PATTERN), 8000);
			final boolean planVisible = waitForVisible(page.getByText(BUSINESS_PLAN_PATTERN), 8000);
			final boolean cambiarPlanVisible = waitForVisible(
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CAMBIAR_PLAN_PATTERN)), 8000);

			boolean nameVisible = false;
			if (sectionVisible) {
				final Locator sectionContainer = nearestContainer(sectionHeader);
				final String sectionText = safeInnerText(sectionContainer);
				nameVisible = containsLikelyName(sectionText);
			}

			final boolean pass = sectionVisible && nameVisible && emailVisible && planVisible && cambiarPlanVisible;
			if (pass) {
				return report.markPassed("Información General", "Name, email, plan text and 'Cambiar Plan' were validated.");
			}
			return report.markFailed("Información General",
					"One or more validations failed (name/email/BUSINESS PLAN/Cambiar Plan).");
		} catch (final RuntimeException ex) {
			return report.markFailed("Información General",
					"Unexpected error while validating Información General: " + ex.getMessage());
		}
	}

	private boolean stepValidateDetallesCuenta(final Page page, final WorkflowReport report) {
		try {
			final boolean cuentaCreada = waitForVisible(page.getByText(CUENTA_CREADA_PATTERN), 8000);
			final boolean estadoActivo = waitForVisible(page.getByText(ESTADO_ACTIVO_PATTERN), 8000);
			final boolean idiomaSeleccionado = waitForVisible(page.getByText(IDIOMA_SELECCIONADO_PATTERN), 8000);
			final boolean pass = cuentaCreada && estadoActivo && idiomaSeleccionado;

			if (pass) {
				return report.markPassed("Detalles de la Cuenta",
						"'Cuenta creada', 'Estado activo' and 'Idioma seleccionado' are visible.");
			}
			return report.markFailed("Detalles de la Cuenta",
					"Missing one or more details: Cuenta creada / Estado activo / Idioma seleccionado.");
		} catch (final RuntimeException ex) {
			return report.markFailed("Detalles de la Cuenta",
					"Unexpected error while validating Detalles de la Cuenta: " + ex.getMessage());
		}
	}

	private boolean stepValidateTusNegocios(final Page page, final WorkflowReport report) {
		try {
			final Locator header = page.getByText(TUS_NEGOCIOS_PATTERN);
			final boolean headerVisible = waitForVisible(header, 8000);
			final boolean agregarButtonVisible = waitForVisible(
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)), 8000)
					|| waitForVisible(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
							8000);
			final boolean limitVisible = waitForVisible(page.getByText(NEGOCIOS_LIMIT_PATTERN), 8000);

			boolean listVisible = false;
			if (headerVisible) {
				final Locator sectionContainer = nearestContainer(header);
				final String text = safeInnerText(sectionContainer);
				listVisible = text.lines().map(String::trim).filter(line -> !line.isEmpty()).count() >= 4;
			}

			final boolean pass = headerVisible && listVisible && agregarButtonVisible && limitVisible;
			if (pass) {
				return report.markPassed("Tus Negocios", "Business list, add button and usage text are visible.");
			}
			return report.markFailed("Tus Negocios",
					"Missing one or more validations: business list / 'Agregar Negocio' / 'Tienes 2 de 3 negocios'.");
		} catch (final RuntimeException ex) {
			return report.markFailed("Tus Negocios", "Unexpected error while validating Tus Negocios: " + ex.getMessage());
		}
	}

	private boolean stepValidateTerminos(final Page page, final WorkflowReport report, final Path runDir) {
		return validateLegalLink(page, runDir, report, "Términos y Condiciones", TERMINOS_PATTERN, TERMINOS_PATTERN,
				"05-terminos-y-condiciones.png");
	}

	private boolean stepValidatePoliticaPrivacidad(final Page page, final WorkflowReport report, final Path runDir) {
		return validateLegalLink(page, runDir, report, "Política de Privacidad", PRIVACIDAD_PATTERN, PRIVACIDAD_PATTERN,
				"06-politica-de-privacidad.png");
	}

	private boolean validateLegalLink(final Page appPage, final Path runDir, final WorkflowReport report,
			final String reportField, final Pattern linkPattern, final Pattern headingPattern, final String screenshotName) {
		try {
			final Locator legalLink = firstVisible(8000,
					appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkPattern)),
					appPage.getByText(linkPattern), appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(linkPattern)));

			if (legalLink == null) {
				return report.markFailed(reportField, "Could not find legal link: " + reportField);
			}

			final String originalUrl = appPage.url();
			final Page openedPage = clickAndCaptureNewPage(appPage, legalLink, 7000);
			final Page legalPage = openedPage == null ? appPage : openedPage;
			waitForUiLoad(legalPage);

			final boolean headingVisible = waitForVisible(
					legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)), 12000)
					|| waitForVisible(legalPage.getByText(headingPattern), 12000);
			final String bodyText = safeInnerText(legalPage.locator("body"));
			final boolean legalContentVisible = bodyText.trim().length() > 200;
			final String finalUrl = legalPage.url();

			takeScreenshot(legalPage, runDir.resolve(screenshotName), true);

			if (openedPage != null && openedPage != appPage) {
				openedPage.close();
				appPage.bringToFront();
				waitForUiLoad(appPage);
			} else if (!originalUrl.equals(appPage.url())) {
				try {
					appPage.goBack();
					waitForUiLoad(appPage);
				} catch (final PlaywrightException ignored) {
					appPage.navigate(originalUrl);
					waitForUiLoad(appPage);
				}
			}

			final boolean pass = headingVisible && legalContentVisible;
			if (pass) {
				return report.markPassed(reportField, "Heading/content validated. Final URL: " + finalUrl);
			}
			return report.markFailed(reportField,
					"Could not validate required legal heading/content. Final URL: " + finalUrl);
		} catch (final RuntimeException ex) {
			return report.markFailed(reportField, "Unexpected error validating legal link: " + ex.getMessage());
		}
	}

	private void waitForMainApp(final Page page, final int timeoutMs) {
		final long startedAt = System.currentTimeMillis();
		while (System.currentTimeMillis() - startedAt < timeoutMs) {
			if (waitForVisible(page.locator("aside"), 1200) && waitForVisible(page.getByText(MI_NEGOCIO_PATTERN), 1200)) {
				return;
			}
			page.waitForTimeout(250);
		}
	}

	private void selectGoogleAccountIfVisible(final Page page) {
		try {
			final Locator accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, new Page.GetByTextOptions().setExact(false));
			if (waitForVisible(accountOption, 8000)) {
				clickAndWait(page, accountOption);
			}
		} catch (final RuntimeException ignored) {
			// The account selector is optional and may not appear.
		}
	}

	private Page clickAndCaptureNewPage(final Page sourcePage, final Locator locator, final int timeoutMs) {
		final BrowserContext context = sourcePage.context();
		final int pagesBefore = context.pages().size();
		clickAndWait(sourcePage, locator);

		final long startedAt = System.currentTimeMillis();
		while (System.currentTimeMillis() - startedAt < timeoutMs) {
			final List<Page> pages = context.pages();
			if (pages.size() > pagesBefore) {
				final Page newPage = pages.get(pages.size() - 1);
				waitForUiLoad(newPage);
				return newPage;
			}
			sourcePage.waitForTimeout(200);
		}
		return null;
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.first().click(new Locator.ClickOptions().setTimeout(15000));
		waitForUiLoad(page);
	}

	private void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (final PlaywrightException ignored) {
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (final PlaywrightException ignored) {
		}
		page.waitForTimeout(500);
	}

	private Locator firstVisible(final int timeoutMs, final Locator... candidates) {
		for (final Locator candidate : candidates) {
			if (candidate != null && waitForVisible(candidate, timeoutMs)) {
				return candidate.first();
			}
		}
		return null;
	}

	private boolean waitForVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.first().waitFor(
					new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout((double) timeoutMs));
			return true;
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private Locator nearestContainer(final Locator target) {
		return target.first().locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
	}

	private String safeInnerText(final Locator locator) {
		try {
			return locator.first().innerText();
		} catch (final PlaywrightException ignored) {
			return "";
		}
	}

	private boolean containsLikelyName(final String sectionText) {
		if (sectionText == null || sectionText.isBlank()) {
			return false;
		}

		final List<String> excludedFragments = Arrays.asList("información general", "informacion general", "business plan",
				"cambiar plan", "cuenta creada", "estado activo", "idioma seleccionado");
		final String[] lines = sectionText.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty() || line.contains("@")) {
				continue;
			}
			final String normalized = line.toLowerCase();
			boolean excluded = false;
			for (final String fragment : excludedFragments) {
				if (normalized.contains(fragment)) {
					excluded = true;
					break;
				}
			}
			if (excluded) {
				continue;
			}
			if (line.matches(".*[\\p{L}]{2,}.*")) {
				return true;
			}
		}
		return false;
	}

	private Path createRunDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path runDir = Paths.get("target", "saleads-e2e", timestamp);
		Files.createDirectories(runDir);
		return runDir;
	}

	private void takeScreenshot(final Page page, final Path path, final boolean fullPage) {
		try {
			Files.createDirectories(path.getParent());
			page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
		} catch (final RuntimeException | IOException ignored) {
			// Screenshot failures are reported indirectly by step validations.
		}
	}

	private String getRequiredEnv(final String name) {
		final String value = System.getenv(name);
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private static final class WorkflowReport {
		private final Path outputPath;
		private final Map<String, StepResult> resultsByField = new LinkedHashMap<>();

		private WorkflowReport(final Path outputPath) {
			this.outputPath = outputPath;
			for (final String field : REPORT_FIELDS) {
				resultsByField.put(field, StepResult.notRun());
			}
		}

		private boolean markPassed(final String field, final String details) {
			resultsByField.put(field, StepResult.pass(details));
			return true;
		}

		private boolean markFailed(final String field, final String details) {
			resultsByField.put(field, StepResult.fail(details));
			return false;
		}

		private boolean markBlocked(final String field, final String details) {
			return markFailed(field, details);
		}

		private boolean allPassed() {
			for (final StepResult result : resultsByField.values()) {
				if (!"PASS".equals(result.status)) {
					return false;
				}
			}
			return true;
		}

		private void write() throws IOException {
			Files.createDirectories(outputPath.getParent());
			Files.writeString(outputPath, toString(), StandardCharsets.UTF_8);
		}

		@Override
		public String toString() {
			final List<String> lines = new ArrayList<>();
			lines.add("saleads_mi_negocio_full_test");
			lines.add("===========================");
			for (final Map.Entry<String, StepResult> entry : resultsByField.entrySet()) {
				lines.add(entry.getKey() + ": " + entry.getValue().status);
				lines.add("  - " + entry.getValue().details);
			}
			return String.join(System.lineSeparator(), lines);
		}
	}

	private static final class StepResult {
		private final String status;
		private final String details;

		private StepResult(final String status, final String details) {
			this.status = status;
			this.details = details;
		}

		private static StepResult pass(final String details) {
			return new StepResult("PASS", details);
		}

		private static StepResult fail(final String details) {
			return new StepResult("FAIL", details);
		}

		private static StepResult notRun() {
			return new StepResult("FAIL", "Step did not execute.");
		}
	}
}
