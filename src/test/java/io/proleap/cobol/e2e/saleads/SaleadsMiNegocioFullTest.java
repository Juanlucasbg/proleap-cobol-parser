package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
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
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

public class SaleadsMiNegocioFullTest {

	private static final String FIELD_LOGIN = "Login";
	private static final String FIELD_MENU = "Mi Negocio menu";
	private static final String FIELD_MODAL = "Agregar Negocio modal";
	private static final String FIELD_ADMIN = "Administrar Negocios view";
	private static final String FIELD_INFO = "Informaci\u00F3n General";
	private static final String FIELD_ACCOUNT = "Detalles de la Cuenta";
	private static final String FIELD_BUSINESSES = "Tus Negocios";
	private static final String FIELD_TERMS = "T\u00E9rminos y Condiciones";
	private static final String FIELD_PRIVACY = "Pol\u00EDtica de Privacidad";

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final boolean enabled = getBooleanConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED");
		Assume.assumeTrue("SaleADS E2E disabled. Enable with -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true.",
				enabled);

		final String loginUrl = getConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		final boolean headless = getBooleanConfig("saleads.headless", "SALEADS_HEADLESS", true);
		final Path runDir = createRunDir();
		final Map<String, StepResult> results = initResults();

		try (Playwright playwright = Playwright.create()) {
			try (Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
					BrowserContext context = browser.newContext()) {
				final Page appPage = context.newPage();
				executeWorkflow(appPage, context, loginUrl, runDir, results);
			}
		} catch (Exception e) {
			markUnexecutedAsPrerequisiteFailure(results, "Unexpected test error: " + safeMessage(e));
		} finally {
			writeReports(runDir, results);
		}

		final List<String> failedFields = collectFailedFields(results);
		if (!failedFields.isEmpty()) {
			fail("SaleADS Mi Negocio workflow failed for fields: " + failedFields + ". See report: "
					+ runDir.resolve("report.md"));
		}
	}

	private void executeWorkflow(final Page appPage, final BrowserContext context, final String loginUrl, final Path runDir,
			final Map<String, StepResult> results) {
		runStepLogin(appPage, context, loginUrl, runDir, results);
		if (!isPass(results, FIELD_LOGIN)) {
			markUnexecutedAsPrerequisiteFailure(results, "Prerequisite failed: Login step did not complete.");
			return;
		}

		runStepMenu(appPage, runDir, results);
		if (!isPass(results, FIELD_MENU)) {
			markUnexecutedAsPrerequisiteFailure(results, "Prerequisite failed: Mi Negocio menu step did not complete.");
			return;
		}

		runStepModal(appPage, runDir, results);
		if (!isPass(results, FIELD_MODAL)) {
			markUnexecutedAsPrerequisiteFailure(results, "Prerequisite failed: Agregar Negocio modal step did not complete.");
			return;
		}

		runStepAdministrarNegocios(appPage, runDir, results);
		if (!isPass(results, FIELD_ADMIN)) {
			markUnexecutedAsPrerequisiteFailure(results,
					"Prerequisite failed: Administrar Negocios view step did not complete.");
			return;
		}

		runStepInformacionGeneral(appPage, results);
		runStepDetallesCuenta(appPage, results);
		runStepTusNegocios(appPage, results);

		runStepLegal(appPage, context, "T[e\\u00E9]rminos y Condiciones", "T[e\\u00E9]rminos y Condiciones",
				"step8_terminos_y_condiciones.png", FIELD_TERMS, runDir, results);
		runStepLegal(appPage, context, "Pol[i\\u00ED]tica de Privacidad", "Pol[i\\u00ED]tica de Privacidad",
				"step9_politica_de_privacidad.png", FIELD_PRIVACY, runDir, results);
	}

	private void runStepLogin(final Page appPage, final BrowserContext context, final String loginUrl, final Path runDir,
			final Map<String, StepResult> results) {
		final StepResult step = results.get(FIELD_LOGIN);
		try {
			if (isBlank(loginUrl)) {
				takeScreenshot(appPage, runDir.resolve("step1_missing_login_url.png"), step);
				setFailure(results, FIELD_LOGIN,
						"Missing login URL. Set SALEADS_LOGIN_URL or -Dsaleads.login.url to current environment login page.");
				return;
			}

			appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitAfterUiAction(appPage);
			takeScreenshot(appPage, runDir.resolve("step0_initial.png"), step);

			Locator loginButton = firstVisible(appPage,
					appPage.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(Pattern.compile("sign in|inicia sesi[o\\u00F3]n|acceder",
									Pattern.CASE_INSENSITIVE))),
					appPage.getByRole(AriaRole.LINK,
							new Page.GetByRoleOptions().setName(Pattern.compile("sign in|inicia sesi[o\\u00F3]n|acceder",
									Pattern.CASE_INSENSITIVE))),
					appPage.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(Pattern.compile("google", Pattern.CASE_INSENSITIVE))));

			if (loginButton == null) {
				setFailure(results, FIELD_LOGIN, "Login button was not found on the provided page.");
				takeScreenshot(appPage, runDir.resolve("step1_after_login_attempt.png"), step);
				return;
			}

			clickAndWait(loginButton, appPage);
			waitAfterUiAction(appPage);
			clickGoogleProviderIfPresent(appPage);
			waitAfterUiAction(appPage);
			maybeChooseGoogleAccount(context, ACCOUNT_EMAIL);
			waitAfterUiAction(appPage);
			takeScreenshot(appPage, runDir.resolve("step1_after_login_attempt.png"), step);

			final boolean mainInterfaceVisible = isVisible(appPage, appPage.locator("main"), appPage.locator("[role='main']"));
			final boolean sidebarVisible = isVisible(appPage, appPage.locator("aside"),
					appPage.getByText(Pattern.compile("Negocio", Pattern.CASE_INSENSITIVE)));

			if (mainInterfaceVisible && sidebarVisible) {
				setPass(results, FIELD_LOGIN, "Dashboard loaded and left sidebar is visible.");
				takeScreenshot(appPage, runDir.resolve("step1_dashboard_loaded.png"), step);
			} else {
				setFailure(results, FIELD_LOGIN,
						"Main interface or sidebar did not become visible after Google login flow.");
			}
		} catch (Exception e) {
			setFailure(results, FIELD_LOGIN, "Login step error: " + safeMessage(e));
		}
	}

	private void runStepMenu(final Page appPage, final Path runDir, final Map<String, StepResult> results) {
		final StepResult step = results.get(FIELD_MENU);
		try {
			final Locator miNegocio = firstVisible(appPage,
					appPage.getByRole(AriaRole.LINK,
							new Page.GetByRoleOptions().setName(Pattern.compile("Mi Negocio", Pattern.CASE_INSENSITIVE))),
					appPage.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(Pattern.compile("Mi Negocio", Pattern.CASE_INSENSITIVE))),
					appPage.getByText(Pattern.compile("^\\s*Mi Negocio\\s*$", Pattern.CASE_INSENSITIVE)));

			if (miNegocio == null) {
				setFailure(results, FIELD_MENU, "'Mi Negocio' option was not found in left sidebar.");
				return;
			}

			clickAndWait(miNegocio, appPage);
			waitAfterUiAction(appPage);

			final boolean agregarVisible = isVisible(appPage,
					appPage.getByRole(AriaRole.LINK,
							new Page.GetByRoleOptions().setName(Pattern.compile("Agregar Negocio", Pattern.CASE_INSENSITIVE))),
					appPage.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(Pattern.compile("Agregar Negocio", Pattern.CASE_INSENSITIVE))),
					appPage.getByText(Pattern.compile("Agregar Negocio", Pattern.CASE_INSENSITIVE)));
			final boolean administrarVisible = isVisible(appPage,
					appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
							.setName(Pattern.compile("Administrar Negocios", Pattern.CASE_INSENSITIVE))),
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
							.setName(Pattern.compile("Administrar Negocios", Pattern.CASE_INSENSITIVE))),
					appPage.getByText(Pattern.compile("Administrar Negocios", Pattern.CASE_INSENSITIVE)));

			takeScreenshot(appPage, runDir.resolve("step2_mi_negocio_menu.png"), step);
			if (agregarVisible && administrarVisible) {
				setPass(results, FIELD_MENU, "Mi Negocio submenu expanded with expected options.");
			} else {
				setFailure(results, FIELD_MENU,
						"Mi Negocio submenu did not show both 'Agregar Negocio' and 'Administrar Negocios'.");
			}
		} catch (Exception e) {
			setFailure(results, FIELD_MENU, "Mi Negocio menu step error: " + safeMessage(e));
		}
	}

	private void runStepModal(final Page appPage, final Path runDir, final Map<String, StepResult> results) {
		final StepResult step = results.get(FIELD_MODAL);
		try {
			final Locator agregarNegocio = firstVisible(appPage,
					appPage.getByRole(AriaRole.LINK,
							new Page.GetByRoleOptions().setName(Pattern.compile("Agregar Negocio", Pattern.CASE_INSENSITIVE))),
					appPage.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(Pattern.compile("Agregar Negocio", Pattern.CASE_INSENSITIVE))),
					appPage.getByText(Pattern.compile("Agregar Negocio", Pattern.CASE_INSENSITIVE)));
			if (agregarNegocio == null) {
				setFailure(results, FIELD_MODAL, "'Agregar Negocio' option was not found.");
				return;
			}

			clickAndWait(agregarNegocio, appPage);
			waitAfterUiAction(appPage);

			final Locator modal = firstVisible(appPage,
					appPage.locator("[role='dialog']"),
					appPage.locator("div:has-text('Crear Nuevo Negocio')"));
			final boolean modalVisible = modal != null && modal.isVisible();
			final boolean titleVisible = isVisible(appPage, appPage.getByText(Pattern.compile("Crear Nuevo Negocio",
					Pattern.CASE_INSENSITIVE)));
			final boolean nombreInputVisible = isVisible(appPage,
					appPage.getByLabel(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE)),
					appPage.getByPlaceholder(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE)),
					appPage.getByText(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE)));
			final boolean quotaVisible = isVisible(appPage,
					appPage.getByText(Pattern.compile("Tienes\\s+2\\s+de\\s+3\\s+negocios", Pattern.CASE_INSENSITIVE)));
			final boolean cancelarVisible = isVisible(appPage, appPage.getByRole(AriaRole.BUTTON,
					new Page.GetByRoleOptions().setName(Pattern.compile("Cancelar", Pattern.CASE_INSENSITIVE))));
			final boolean crearVisible = isVisible(appPage, appPage.getByRole(AriaRole.BUTTON,
					new Page.GetByRoleOptions().setName(Pattern.compile("Crear Negocio", Pattern.CASE_INSENSITIVE))));

			takeScreenshot(appPage, runDir.resolve("step3_agregar_negocio_modal.png"), step);

			if (modalVisible && titleVisible && nombreInputVisible && quotaVisible && cancelarVisible && crearVisible) {
				// Optional actions requested by workflow.
				final Locator nombreInput = firstVisible(appPage,
						appPage.getByLabel(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE)),
						appPage.getByPlaceholder(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE)));
				if (nombreInput != null) {
					nombreInput.click();
					waitAfterUiAction(appPage);
					nombreInput.fill("Negocio Prueba Automatizacion");
					waitAfterUiAction(appPage);
				}
				final Locator cancelar = appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("Cancelar", Pattern.CASE_INSENSITIVE)));
				clickAndWait(cancelar, appPage);
				waitAfterUiAction(appPage);

				setPass(results, FIELD_MODAL, "Crear Nuevo Negocio modal validated successfully.");
			} else {
				setFailure(results, FIELD_MODAL,
						"Modal validation failed. Missing one or more required fields/buttons/texts.");
			}
		} catch (Exception e) {
			setFailure(results, FIELD_MODAL, "Agregar Negocio modal step error: " + safeMessage(e));
		}
	}

	private void runStepAdministrarNegocios(final Page appPage, final Path runDir, final Map<String, StepResult> results) {
		final StepResult step = results.get(FIELD_ADMIN);
		try {
			expandMiNegocioIfCollapsed(appPage);

			final Locator administrarNegocios = firstVisible(appPage,
					appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
							.setName(Pattern.compile("Administrar Negocios", Pattern.CASE_INSENSITIVE))),
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
							.setName(Pattern.compile("Administrar Negocios", Pattern.CASE_INSENSITIVE))),
					appPage.getByText(Pattern.compile("Administrar Negocios", Pattern.CASE_INSENSITIVE)));
			if (administrarNegocios == null) {
				setFailure(results, FIELD_ADMIN, "'Administrar Negocios' option was not found.");
				return;
			}

			clickAndWait(administrarNegocios, appPage);
			waitAfterUiAction(appPage);

			final boolean infoGeneral = isVisible(appPage,
					appPage.getByText(Pattern.compile("Informaci[o\\u00F3]n General", Pattern.CASE_INSENSITIVE)));
			final boolean detallesCuenta = isVisible(appPage,
					appPage.getByText(Pattern.compile("Detalles de la Cuenta", Pattern.CASE_INSENSITIVE)));
			final boolean tusNegocios = isVisible(appPage,
					appPage.getByText(Pattern.compile("Tus Negocios", Pattern.CASE_INSENSITIVE)));
			final boolean seccionLegal = isVisible(appPage,
					appPage.getByText(Pattern.compile("Secci[o\\u00F3]n Legal", Pattern.CASE_INSENSITIVE)));

			takeScreenshot(appPage, runDir.resolve("step4_administrar_negocios.png"), step);
			if (infoGeneral && detallesCuenta && tusNegocios && seccionLegal) {
				setPass(results, FIELD_ADMIN, "Administrar Negocios view loaded with all expected sections.");
			} else {
				setFailure(results, FIELD_ADMIN,
						"Administrar Negocios page is missing one or more required sections.");
			}
		} catch (Exception e) {
			setFailure(results, FIELD_ADMIN, "Administrar Negocios step error: " + safeMessage(e));
		}
	}

	private void runStepInformacionGeneral(final Page appPage, final Map<String, StepResult> results) {
		try {
			final Locator infoSection = sectionByHeading(appPage, "Informaci[o\\u00F3]n General");
			if (infoSection == null) {
				setFailure(results, FIELD_INFO, "Informacion General section was not found.");
				return;
			}

			final String text = safeText(infoSection);
			final boolean emailVisible = EMAIL_PATTERN.matcher(text).find();
			final boolean businessPlanVisible = Pattern.compile("BUSINESS PLAN", Pattern.CASE_INSENSITIVE).matcher(text)
					.find();
			final boolean cambiarPlanVisible = isVisible(infoSection, infoSection.getByRole(AriaRole.BUTTON,
					new Locator.GetByRoleOptions().setName(Pattern.compile("Cambiar Plan", Pattern.CASE_INSENSITIVE))));
			final boolean userNameVisible = hasNameLikeText(text);

			if (userNameVisible && emailVisible && businessPlanVisible && cambiarPlanVisible) {
				setPass(results, FIELD_INFO, "Informacion General validated successfully.");
			} else {
				setFailure(results, FIELD_INFO,
						"Informacion General missing user name/email/BUSINESS PLAN/Cambiar Plan.");
			}
		} catch (Exception e) {
			setFailure(results, FIELD_INFO, "Informacion General validation error: " + safeMessage(e));
		}
	}

	private void runStepDetallesCuenta(final Page appPage, final Map<String, StepResult> results) {
		try {
			final Locator section = sectionByHeading(appPage, "Detalles de la Cuenta");
			if (section == null) {
				setFailure(results, FIELD_ACCOUNT, "Detalles de la Cuenta section was not found.");
				return;
			}

			final String text = safeText(section);
			final boolean cuentaCreadaVisible = Pattern.compile("Cuenta creada", Pattern.CASE_INSENSITIVE).matcher(text)
					.find();
			final boolean estadoActivoVisible = Pattern.compile("Estado activo", Pattern.CASE_INSENSITIVE).matcher(text)
					.find();
			final boolean idiomaVisible = Pattern.compile("Idioma seleccionado", Pattern.CASE_INSENSITIVE).matcher(text)
					.find();

			if (cuentaCreadaVisible && estadoActivoVisible && idiomaVisible) {
				setPass(results, FIELD_ACCOUNT, "Detalles de la Cuenta validated successfully.");
			} else {
				setFailure(results, FIELD_ACCOUNT,
						"Detalles de la Cuenta missing Cuenta creada / Estado activo / Idioma seleccionado.");
			}
		} catch (Exception e) {
			setFailure(results, FIELD_ACCOUNT, "Detalles de la Cuenta validation error: " + safeMessage(e));
		}
	}

	private void runStepTusNegocios(final Page appPage, final Map<String, StepResult> results) {
		try {
			final Locator section = sectionByHeading(appPage, "Tus Negocios");
			if (section == null) {
				setFailure(results, FIELD_BUSINESSES, "Tus Negocios section was not found.");
				return;
			}

			final String text = safeText(section);
			final boolean listVisible = text != null && text.trim().length() > 20;
			final boolean agregarVisible = isVisible(section, section.getByRole(AriaRole.BUTTON,
					new Locator.GetByRoleOptions().setName(Pattern.compile("Agregar Negocio", Pattern.CASE_INSENSITIVE))),
					section.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions()
							.setName(Pattern.compile("Agregar Negocio", Pattern.CASE_INSENSITIVE))),
					section.getByText(Pattern.compile("Agregar Negocio", Pattern.CASE_INSENSITIVE)));
			final boolean quotaVisible = Pattern.compile("Tienes\\s+2\\s+de\\s+3\\s+negocios", Pattern.CASE_INSENSITIVE)
					.matcher(text).find();

			if (listVisible && agregarVisible && quotaVisible) {
				setPass(results, FIELD_BUSINESSES, "Tus Negocios validated successfully.");
			} else {
				setFailure(results, FIELD_BUSINESSES,
						"Tus Negocios missing list visibility / Agregar Negocio / quota text.");
			}
		} catch (Exception e) {
			setFailure(results, FIELD_BUSINESSES, "Tus Negocios validation error: " + safeMessage(e));
		}
	}

	private void runStepLegal(final Page appPage, final BrowserContext context, final String linkLabelRegex,
			final String headingRegex, final String screenshotName, final String field, final Path runDir,
			final Map<String, StepResult> results) {
		final StepResult step = results.get(field);
		try {
			final Locator legalLink = firstVisible(appPage,
					appPage.getByRole(AriaRole.LINK,
							new Page.GetByRoleOptions().setName(Pattern.compile(linkLabelRegex, Pattern.CASE_INSENSITIVE))),
					appPage.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(Pattern.compile(linkLabelRegex, Pattern.CASE_INSENSITIVE))),
					appPage.getByText(Pattern.compile(linkLabelRegex, Pattern.CASE_INSENSITIVE)));
			if (legalLink == null) {
				setFailure(results, field, "Legal link was not found: " + linkLabelRegex);
				return;
			}

			Page legalPage = null;
			boolean openedNewTab = false;
			try {
				legalPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(4000),
						() -> clickAndWait(legalLink, appPage));
				openedNewTab = true;
				legalPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
			} catch (TimeoutError timeout) {
				clickAndWait(legalLink, appPage);
				waitAfterUiAction(appPage);
				legalPage = appPage;
			}

			waitAfterUiAction(legalPage);
			final boolean headingVisible = isVisible(legalPage,
					legalPage.getByRole(AriaRole.HEADING,
							new Page.GetByRoleOptions().setName(Pattern.compile(headingRegex, Pattern.CASE_INSENSITIVE))),
					legalPage.getByText(Pattern.compile(headingRegex, Pattern.CASE_INSENSITIVE)));
			final String legalText = safeText(legalPage.locator("body"));
			final boolean contentVisible = legalText != null && legalText.trim().length() > 120;

			takeScreenshot(legalPage, runDir.resolve(screenshotName), step);
			step.finalUrl = legalPage.url();

			if (headingVisible && contentVisible) {
				setPass(results, field, "Legal page validated successfully.");
			} else {
				setFailure(results, field, "Legal page missing heading/content validation.");
			}

			if (openedNewTab) {
				legalPage.close();
				appPage.bringToFront();
				waitAfterUiAction(appPage);
			} else {
				try {
					appPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
				} catch (Exception ignored) {
					// Same-tab legal pages may not allow a stable history back operation.
				}
				waitAfterUiAction(appPage);
			}
		} catch (Exception e) {
			setFailure(results, field, "Legal validation error: " + safeMessage(e));
		}
	}

	private void clickGoogleProviderIfPresent(final Page appPage) {
		Locator provider = firstVisible(appPage,
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("google", Pattern.CASE_INSENSITIVE))),
				appPage.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile("google", Pattern.CASE_INSENSITIVE))),
				appPage.getByText(Pattern.compile("^\\s*google\\s*$", Pattern.CASE_INSENSITIVE)));
		if (provider != null) {
			clickAndWait(provider, appPage);
			return;
		}

		for (final Frame frame : appPage.frames()) {
			try {
				final Locator frameGoogle = firstVisible(frame,
						frame.getByRole(AriaRole.BUTTON,
								new Frame.GetByRoleOptions().setName(Pattern.compile("google", Pattern.CASE_INSENSITIVE))),
						frame.getByRole(AriaRole.LINK,
								new Frame.GetByRoleOptions().setName(Pattern.compile("google", Pattern.CASE_INSENSITIVE))),
						frame.getByText(Pattern.compile("^\\s*google\\s*$", Pattern.CASE_INSENSITIVE)));
				if (frameGoogle != null) {
					frameGoogle.click();
					waitAfterUiAction(appPage);
					return;
				}
			} catch (Exception ignored) {
				// Continue trying next frame.
			}
		}
	}

	private void maybeChooseGoogleAccount(final BrowserContext context, final String accountEmail) {
		for (final Page page : context.pages()) {
			try {
				final Locator accountOption = page.getByText(Pattern.compile(Pattern.quote(accountEmail)));
				if (accountOption.first().isVisible()) {
					clickAndWait(accountOption.first(), page);
					return;
				}
			} catch (Exception ignored) {
				// continue
			}
		}
	}

	private void expandMiNegocioIfCollapsed(final Page appPage) {
		final boolean collapsed = !isVisible(appPage,
				appPage.getByText(Pattern.compile("Agregar Negocio", Pattern.CASE_INSENSITIVE)),
				appPage.getByText(Pattern.compile("Administrar Negocios", Pattern.CASE_INSENSITIVE)));
		if (collapsed) {
			final Locator miNegocio = firstVisible(appPage,
					appPage.getByRole(AriaRole.LINK,
							new Page.GetByRoleOptions().setName(Pattern.compile("Mi Negocio", Pattern.CASE_INSENSITIVE))),
					appPage.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(Pattern.compile("Mi Negocio", Pattern.CASE_INSENSITIVE))),
					appPage.getByText(Pattern.compile("^\\s*Mi Negocio\\s*$", Pattern.CASE_INSENSITIVE)));
			if (miNegocio != null) {
				clickAndWait(miNegocio, appPage);
				waitAfterUiAction(appPage);
			}
		}
	}

	private Locator sectionByHeading(final Page page, final String headingRegex) {
		final Locator heading = firstVisible(page,
				page.getByRole(AriaRole.HEADING,
						new Page.GetByRoleOptions().setName(Pattern.compile(headingRegex, Pattern.CASE_INSENSITIVE))),
				page.getByText(Pattern.compile(headingRegex, Pattern.CASE_INSENSITIVE)));
		if (heading == null) {
			return null;
		}

		try {
			final Locator candidate = heading.locator("xpath=ancestor::*[self::section or self::div][1]");
			return candidate.isVisible() ? candidate : heading;
		} catch (Exception ignored) {
			return heading;
		}
	}

	private void clickAndWait(final Locator locator, final Page page) {
		locator.click();
		waitAfterUiAction(page);
	}

	private void waitAfterUiAction(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7000));
		} catch (Exception ignored) {
			// Not every action causes network activity.
		}
		page.waitForTimeout(800);
	}

	private void takeScreenshot(final Page page, final Path path, final StepResult result) {
		try {
			page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(true));
			result.screenshots.add(path.toString());
		} catch (Exception ignored) {
			// Keep execution going even if screenshot capture fails.
		}
	}

	private void setPass(final Map<String, StepResult> results, final String field, final String details) {
		final StepResult step = results.get(field);
		step.status = "PASS";
		step.details = details;
	}

	private void setFailure(final Map<String, StepResult> results, final String field, final String details) {
		final StepResult step = results.get(field);
		step.status = "FAIL";
		step.details = details;
	}

	private boolean isPass(final Map<String, StepResult> results, final String field) {
		return "PASS".equals(results.get(field).status);
	}

	private void markUnexecutedAsPrerequisiteFailure(final Map<String, StepResult> results, final String details) {
		for (final StepResult step : results.values()) {
			if ("NOT_EXECUTED".equals(step.status)) {
				step.status = "FAIL";
				step.details = details;
			}
		}
	}

	private Map<String, StepResult> initResults() {
		final Map<String, StepResult> results = new LinkedHashMap<>();
		for (final String field : Arrays.asList(FIELD_LOGIN, FIELD_MENU, FIELD_MODAL, FIELD_ADMIN, FIELD_INFO, FIELD_ACCOUNT,
				FIELD_BUSINESSES, FIELD_TERMS, FIELD_PRIVACY)) {
			final StepResult step = new StepResult();
			step.status = "NOT_EXECUTED";
			step.details = "Not executed yet.";
			results.put(field, step);
		}
		return results;
	}

	private Path createRunDir() throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
				.format(Instant.now());
		final Path runDir = Paths.get("target", "saleads-mi-negocio", timestamp);
		Files.createDirectories(runDir);
		return runDir;
	}

	private void writeReports(final Path runDir, final Map<String, StepResult> results) {
		try {
			Files.writeString(runDir.resolve("report.md"), buildMarkdown(results), StandardCharsets.UTF_8);
			Files.writeString(runDir.resolve("report.json"), buildJson(results), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException("Failed to write SaleADS reports to " + runDir, e);
		}
	}

	private String buildMarkdown(final Map<String, StepResult> results) {
		final StringBuilder sb = new StringBuilder();
		sb.append("# SaleADS Mi Negocio Full Test Report\n\n");
		sb.append("| Field | Status | Details | Final URL | Screenshots |\n");
		sb.append("| --- | --- | --- | --- | --- |\n");
		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			final StepResult step = entry.getValue();
			sb.append("| ").append(entry.getKey()).append(" | ");
			sb.append(step.status).append(" | ");
			sb.append(escapeMarkdown(step.details)).append(" | ");
			sb.append(step.finalUrl == null ? "" : step.finalUrl).append(" | ");
			sb.append(step.screenshots.isEmpty() ? "" : String.join("<br>", step.screenshots));
			sb.append(" |\n");
		}
		return sb.toString();
	}

	private String buildJson(final Map<String, StepResult> results) {
		final StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		sb.append("  \"workflow\": \"saleads_mi_negocio_full_test\",\n");
		sb.append("  \"steps\": [\n");

		int idx = 0;
		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			final StepResult step = entry.getValue();
			if (idx > 0) {
				sb.append(",\n");
			}
			sb.append("    {\n");
			sb.append("      \"field\": \"").append(jsonEscape(entry.getKey())).append("\",\n");
			sb.append("      \"status\": \"").append(jsonEscape(step.status)).append("\",\n");
			sb.append("      \"details\": \"").append(jsonEscape(step.details)).append("\",\n");
			sb.append("      \"finalUrl\": ").append(step.finalUrl == null ? "null" : "\"" + jsonEscape(step.finalUrl) + "\"")
					.append(",\n");
			sb.append("      \"screenshots\": [");
			for (int i = 0; i < step.screenshots.size(); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				sb.append("\"").append(jsonEscape(step.screenshots.get(i))).append("\"");
			}
			sb.append("]\n");
			sb.append("    }");
			idx++;
		}

		sb.append("\n  ]\n");
		sb.append("}\n");
		return sb.toString();
	}

	private List<String> collectFailedFields(final Map<String, StepResult> results) {
		final List<String> failed = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			if (!"PASS".equals(entry.getValue().status)) {
				failed.add(entry.getKey());
			}
		}
		return failed;
	}

	private boolean hasNameLikeText(final String sectionText) {
		if (sectionText == null) {
			return false;
		}
		for (final String line : sectionText.split("\\R")) {
			final String normalized = line.trim();
			if (normalized.isEmpty()) {
				continue;
			}
			if (normalized.length() < 3 || normalized.length() > 80) {
				continue;
			}
			if (normalized.contains("@")) {
				continue;
			}
			if (Pattern.compile("Informaci[o\\u00F3]n General|BUSINESS PLAN|Cambiar Plan|Cuenta creada|Estado activo|Idioma",
					Pattern.CASE_INSENSITIVE).matcher(normalized).find()) {
				continue;
			}
			if (Pattern.compile("[A-Za-z]{2,}\\s+[A-Za-z]{2,}").matcher(normalized).find()) {
				return true;
			}
		}
		return false;
	}

	private boolean isVisible(final Page page, final Locator... locators) {
		for (final Locator locator : locators) {
			if (locator == null) {
				continue;
			}
			try {
				if (locator.first().isVisible()) {
					return true;
				}
			} catch (Exception ignored) {
				// try next locator
			}
		}
		return false;
	}

	private boolean isVisible(final Locator section, final Locator... locators) {
		for (final Locator locator : locators) {
			if (locator == null) {
				continue;
			}
			try {
				if (locator.first().isVisible()) {
					return true;
				}
			} catch (Exception ignored) {
				// try next locator
			}
		}
		try {
			return section.first().isVisible();
		} catch (Exception ignored) {
			return false;
		}
	}

	private Locator firstVisible(final Page page, final Locator... candidates) {
		for (final Locator candidate : candidates) {
			if (candidate == null) {
				continue;
			}
			try {
				final Locator first = candidate.first();
				if (first.isVisible()) {
					return first;
				}
			} catch (Exception ignored) {
				// continue
			}
		}
		return null;
	}

	private Locator firstVisible(final Frame frame, final Locator... candidates) {
		for (final Locator candidate : candidates) {
			if (candidate == null) {
				continue;
			}
			try {
				final Locator first = candidate.first();
				if (first.isVisible()) {
					return first;
				}
			} catch (Exception ignored) {
				// continue
			}
		}
		return null;
	}

	private String safeText(final Locator locator) {
		try {
			final String text = locator.textContent();
			return text == null ? "" : text;
		} catch (Exception e) {
			return "";
		}
	}

	private String getConfig(final String systemProperty, final String envVar) {
		final String fromProperty = System.getProperty(systemProperty);
		if (!isBlank(fromProperty)) {
			return fromProperty.trim();
		}
		final String fromEnv = System.getenv(envVar);
		if (!isBlank(fromEnv)) {
			return fromEnv.trim();
		}
		return "";
	}

	private boolean getBooleanConfig(final String systemProperty, final String envVar) {
		return getBooleanConfig(systemProperty, envVar, false);
	}

	private boolean getBooleanConfig(final String systemProperty, final String envVar, final boolean defaultValue) {
		final String raw = getConfig(systemProperty, envVar);
		if (isBlank(raw)) {
			return defaultValue;
		}
		return "1".equals(raw) || "true".equalsIgnoreCase(raw) || "yes".equalsIgnoreCase(raw);
	}

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	private String safeMessage(final Exception exception) {
		final String message = exception.getMessage();
		return message == null ? exception.getClass().getSimpleName() : message;
	}

	private String escapeMarkdown(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace("|", "\\|").replace("\n", "<br>").replace("\r", "");
	}

	private String jsonEscape(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
				.replace("\t", "\\t");
	}

	private static final class StepResult {
		private String status;
		private String details;
		private String finalUrl;
		private final List<String> screenshots = new ArrayList<>();
	}

}
