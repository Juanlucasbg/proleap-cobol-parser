package io.proleap.cobol.e2e;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL with the current SaleADS login page URL.",
				loginUrl != null && !loginUrl.isBlank());

		final Path evidenceDir = createEvidenceDirectory();
		final Map<String, StepResult> report = initializeReport();

		String termsUrl = "N/A";
		String privacyUrl = "N/A";

		try (Playwright playwright = Playwright.create()) {
			final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "false"));
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext();
			final Page page = context.newPage();

			page.navigate(loginUrl);
			waitForUiLoad(page);

			report.put("Login", loginStep(page, context, evidenceDir));
			report.put("Mi Negocio menu", miNegocioMenuStep(page, evidenceDir));
			report.put("Agregar Negocio modal", agregarNegocioModalStep(page, evidenceDir));
			report.put("Administrar Negocios view", administrarNegociosStep(page, evidenceDir));
			report.put("Información General", informacionGeneralStep(page));
			report.put("Detalles de la Cuenta", detallesCuentaStep(page));
			report.put("Tus Negocios", tusNegociosStep(page));

			LegalValidationResult termsResult = legalLinkStep(page, context, evidenceDir, "Términos y Condiciones",
					Pattern.compile("(?iu)T[ée]rminos\\s+y\\s+Condiciones"), "step8_terminos.png");
			report.put("Términos y Condiciones", termsResult.result);
			termsUrl = termsResult.url;

			LegalValidationResult privacyResult = legalLinkStep(page, context, evidenceDir, "Política de Privacidad",
					Pattern.compile("(?iu)Pol[íi]tica\\s+de\\s+Privacidad"), "step9_privacidad.png");
			report.put("Política de Privacidad", privacyResult.result);
			privacyUrl = privacyResult.url;
		}

		Path reportFile = writeReport(evidenceDir, report, termsUrl, privacyUrl);
		assertEveryStepPassed(report, reportFile);
	}

	private StepResult loginStep(final Page page, final BrowserContext context, final Path evidenceDir) {
		try {
			Locator loginButton = firstVisible(List.of(
					page.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*google.*"))),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*google.*"))),
					page.getByText(Pattern.compile("(?iu)(sign\\s*in\\s*with\\s*google|iniciar\\s+sesi[óo]n\\s+con\\s+google)"))),
					2000);

			if (loginButton == null) {
				return StepResult.fail("Could not find Google sign-in control.");
			}

			int beforeClickPageCount = context.pages().size();
			clickAndWait(loginButton, page);
			Page popup = waitForNewPage(context, beforeClickPageCount, 12000);
			if (popup != null) {
				waitForUiLoad(popup);
				selectGoogleAccountIfVisible(popup);
				long popupCloseDeadline = System.currentTimeMillis() + 20000;
				while (!popup.isClosed() && System.currentTimeMillis() <= popupCloseDeadline) {
					popup.waitForTimeout(250);
				}
				if (!popup.isClosed()) {
					popup.close();
				}
				page.bringToFront();
				waitForUiLoad(page);
			} else {
				selectGoogleAccountIfVisible(page);
				waitForUiLoad(page);
			}

			boolean mainVisible = isVisible(page.locator("main"), 10000) || isVisible(page.locator("body"), 10000);
			boolean sidebarVisible = isVisible(page.locator("aside"), 10000)
					|| isVisible(page.getByText(Pattern.compile("(?iu)Negocio")), 10000);
			takeScreenshot(page, evidenceDir, "step1_dashboard_loaded.png", true);

			if (mainVisible && sidebarVisible) {
				return StepResult.pass("Main interface and sidebar are visible.");
			}
			return StepResult.fail("Dashboard did not render as expected after Google login.");
		} catch (Exception ex) {
			return StepResult.fail("Login step failed: " + ex.getMessage());
		}
	}

	private StepResult miNegocioMenuStep(final Page page, final Path evidenceDir) {
		try {
			Locator negocioSection = firstVisible(List.of(
					page.getByText(Pattern.compile("(?iu)^\\s*Negocio\\s*$")),
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Negocio"))),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Negocio")))),
					2000);
			if (negocioSection != null) {
				clickAndWait(negocioSection, page);
			}

			Locator miNegocio = firstVisible(List.of(
					page.getByText(Pattern.compile("(?iu)Mi\\s+Negocio")),
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Mi\\s+Negocio"))),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Mi\\s+Negocio")))),
					5000);

			if (miNegocio == null) {
				return StepResult.fail("Could not find 'Mi Negocio' option in sidebar.");
			}

			clickAndWait(miNegocio, page);
			boolean agregarVisible = isVisible(page.getByText(Pattern.compile("(?iu)Agregar\\s+Negocio")), 5000);
			boolean administrarVisible = isVisible(page.getByText(Pattern.compile("(?iu)Administrar\\s+Negocios")), 5000);
			takeScreenshot(page, evidenceDir, "step2_mi_negocio_menu_expanded.png", true);

			if (agregarVisible && administrarVisible) {
				return StepResult.pass("Menu expanded with Agregar/Administrar options visible.");
			}
			return StepResult.fail("Submenu did not expose both expected options.");
		} catch (Exception ex) {
			return StepResult.fail("Mi Negocio menu step failed: " + ex.getMessage());
		}
	}

	private StepResult agregarNegocioModalStep(final Page page, final Path evidenceDir) {
		try {
			Locator agregarNegocio = firstVisible(List.of(
					page.getByText(Pattern.compile("(?iu)^\\s*Agregar\\s+Negocio\\s*$")),
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Agregar\\s+Negocio"))),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Agregar\\s+Negocio")))),
					5000);
			if (agregarNegocio == null) {
				return StepResult.fail("Could not find 'Agregar Negocio' option.");
			}

			clickAndWait(agregarNegocio, page);

			boolean titleVisible = isVisible(page.getByText(Pattern.compile("(?iu)Crear\\s+Nuevo\\s+Negocio")), 10000);
			boolean nombreInputVisible = isVisible(page.getByLabel(Pattern.compile("(?iu)Nombre\\s+del\\s+Negocio")), 3000)
					|| isVisible(page.getByPlaceholder(Pattern.compile("(?iu)Nombre\\s+del\\s+Negocio")), 3000);
			boolean quotaVisible = isVisible(page.getByText(Pattern.compile("(?iu)Tienes\\s+2\\s+de\\s+3\\s+negocios")), 3000);
			boolean cancelarVisible = isVisible(
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Cancelar"))),
					3000);
			boolean crearVisible = isVisible(page.getByRole(AriaRole.BUTTON,
					new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Crear\\s+Negocio"))), 3000);

			takeScreenshot(page, evidenceDir, "step3_agregar_negocio_modal.png", true);

			Locator nombreInput = firstVisible(List.of(
					page.getByLabel(Pattern.compile("(?iu)Nombre\\s+del\\s+Negocio")),
					page.getByPlaceholder(Pattern.compile("(?iu)Nombre\\s+del\\s+Negocio"))), 3000);
			if (nombreInput != null) {
				nombreInput.fill("Negocio Prueba Automatización");
			}
			Locator cancelar = firstVisible(List.of(
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Cancelar")))),
					2000);
			if (cancelar != null) {
				clickAndWait(cancelar, page);
			}

			if (titleVisible && nombreInputVisible && quotaVisible && cancelarVisible && crearVisible) {
				return StepResult.pass("Crear Nuevo Negocio modal content is valid.");
			}
			return StepResult.fail("Modal is missing one or more required controls/texts.");
		} catch (Exception ex) {
			return StepResult.fail("Agregar Negocio modal step failed: " + ex.getMessage());
		}
	}

	private StepResult administrarNegociosStep(final Page page, final Path evidenceDir) {
		try {
			Locator administrarNegocios = firstVisible(List.of(
					page.getByText(Pattern.compile("(?iu)^\\s*Administrar\\s+Negocios\\s*$")),
					page.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Administrar\\s+Negocios"))),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Administrar\\s+Negocios")))),
					5000);

			if (administrarNegocios == null) {
				Locator miNegocio = firstVisible(List.of(page.getByText(Pattern.compile("(?iu)Mi\\s+Negocio"))), 3000);
				if (miNegocio != null) {
					clickAndWait(miNegocio, page);
				}
				administrarNegocios = firstVisible(List.of(
						page.getByText(Pattern.compile("(?iu)^\\s*Administrar\\s+Negocios\\s*$")),
						page.getByRole(AriaRole.BUTTON,
								new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Administrar\\s+Negocios"))),
						page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
								.setName(Pattern.compile("(?iu)Administrar\\s+Negocios")))), 5000);
			}

			if (administrarNegocios == null) {
				return StepResult.fail("Could not find 'Administrar Negocios' option.");
			}

			clickAndWait(administrarNegocios, page);

			boolean infoGeneral = isVisible(page.getByText(Pattern.compile("(?iu)Informaci[óo]n\\s+General")), 7000);
			boolean detallesCuenta = isVisible(page.getByText(Pattern.compile("(?iu)Detalles\\s+de\\s+la\\s+Cuenta")), 7000);
			boolean tusNegocios = isVisible(page.getByText(Pattern.compile("(?iu)Tus\\s+Negocios")), 7000);
			boolean seccionLegal = isVisible(page.getByText(Pattern.compile("(?iu)Secci[óo]n\\s+Legal")), 7000);
			takeScreenshot(page, evidenceDir, "step4_administrar_negocios_view.png", true);

			if (infoGeneral && detallesCuenta && tusNegocios && seccionLegal) {
				return StepResult.pass("Administrar Negocios view contains all required sections.");
			}
			return StepResult.fail("One or more required account sections are missing.");
		} catch (Exception ex) {
			return StepResult.fail("Administrar Negocios step failed: " + ex.getMessage());
		}
	}

	private StepResult informacionGeneralStep(final Page page) {
		try {
			final String bodyText = page.locator("body").innerText();
			boolean hasEmail = Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}").matcher(bodyText).find();
			boolean hasUserName = Pattern.compile("(?iu)(nombre\\s*[:\\n]|usuario\\s*[:\\n]|perfil)").matcher(bodyText)
					.find();
			boolean hasPlan = isVisible(page.getByText(Pattern.compile("(?iu)BUSINESS\\s+PLAN")), 4000);
			boolean hasCambiarPlan = isVisible(
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Cambiar\\s+Plan"))),
					4000) || isVisible(page.getByText(Pattern.compile("(?iu)Cambiar\\s+Plan")), 4000);

			if (hasUserName && hasEmail && hasPlan && hasCambiarPlan) {
				return StepResult.pass("Información General content validated.");
			}
			return StepResult.fail("Información General missing username/email/plan information.");
		} catch (Exception ex) {
			return StepResult.fail("Información General validation failed: " + ex.getMessage());
		}
	}

	private StepResult detallesCuentaStep(final Page page) {
		try {
			boolean cuentaCreada = isVisible(page.getByText(Pattern.compile("(?iu)Cuenta\\s+creada")), 4000);
			boolean estadoActivo = isVisible(page.getByText(Pattern.compile("(?iu)Estado\\s+activo")), 4000);
			boolean idiomaSeleccionado = isVisible(page.getByText(Pattern.compile("(?iu)Idioma\\s+seleccionado")), 4000);
			if (cuentaCreada && estadoActivo && idiomaSeleccionado) {
				return StepResult.pass("Detalles de la Cuenta validated.");
			}
			return StepResult.fail("Detalles de la Cuenta is missing one or more required fields.");
		} catch (Exception ex) {
			return StepResult.fail("Detalles de la Cuenta validation failed: " + ex.getMessage());
		}
	}

	private StepResult tusNegociosStep(final Page page) {
		try {
			boolean sectionVisible = isVisible(page.getByText(Pattern.compile("(?iu)Tus\\s+Negocios")), 4000);
			boolean addButton = isVisible(
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Agregar\\s+Negocio"))),
					4000) || isVisible(page.getByText(Pattern.compile("(?iu)Agregar\\s+Negocio")), 4000);
			boolean quota = isVisible(page.getByText(Pattern.compile("(?iu)Tienes\\s+2\\s+de\\s+3\\s+negocios")), 4000);
			String bodyText = page.locator("body").innerText();
			boolean hasBusinessListSignals = Pattern.compile("(?iu)(Negocio\\s+\\d+|Administrar\\s+Negocios|Tus\\s+Negocios)")
					.matcher(bodyText).find();

			if (sectionVisible && addButton && quota && hasBusinessListSignals) {
				return StepResult.pass("Tus Negocios section validated.");
			}
			return StepResult.fail("Tus Negocios section did not meet all expected validations.");
		} catch (Exception ex) {
			return StepResult.fail("Tus Negocios validation failed: " + ex.getMessage());
		}
	}

	private LegalValidationResult legalLinkStep(final Page page, final BrowserContext context, final Path evidenceDir,
			final String linkText, final Pattern headingPattern, final String screenshotName) {
		try {
			Locator legalLink = firstVisible(List.of(
					page.getByText(Pattern.compile("(?iu)^\\s*" + Pattern.quote(linkText) + "\\s*$")),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)" + Pattern.quote(linkText)))),
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)" + Pattern.quote(linkText))))),
					5000);
			if (legalLink == null) {
				return new LegalValidationResult(StepResult.fail("Legal link not found: " + linkText), "N/A");
			}

			int beforeClickPageCount = context.pages().size();
			String appUrlBeforeClick = page.url();
			clickAndWait(legalLink, page);
			Page legalPage = waitForNewPage(context, beforeClickPageCount, 10000);
			if (legalPage == null) {
				legalPage = page;
			}
			waitForUiLoad(legalPage);

			boolean headingVisible = isVisible(legalPage.getByRole(AriaRole.HEADING,
					new Page.GetByRoleOptions().setName(headingPattern)), 7000)
					|| isVisible(legalPage.getByText(headingPattern), 7000);
			String legalPageText = legalPage.locator("body").innerText();
			boolean hasLegalContent = legalPageText != null && legalPageText.trim().length() > 200;
			takeScreenshot(legalPage, evidenceDir, screenshotName, true);
			String finalUrl = legalPage.url();

			if (legalPage != page) {
				legalPage.close();
				page.bringToFront();
				waitForUiLoad(page);
			} else if (!appUrlBeforeClick.equals(page.url())) {
				try {
					page.goBack();
					waitForUiLoad(page);
				} catch (PlaywrightException ignored) {
					// do not fail cleanup when browser history is unavailable
				}
			}

			if (headingVisible && hasLegalContent) {
				return new LegalValidationResult(StepResult.pass("Validated legal page for " + linkText), finalUrl);
			}
			return new LegalValidationResult(StepResult.fail("Invalid legal page content for " + linkText), finalUrl);
		} catch (Exception ex) {
			return new LegalValidationResult(StepResult.fail("Legal validation failed for " + linkText + ": " + ex.getMessage()),
					"N/A");
		}
	}

	private Path createEvidenceDirectory() throws IOException {
		final String runId = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
		Path evidenceDir = Paths.get("target", "saleads-mi-negocio-evidence", runId);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private Map<String, StepResult> initializeReport() {
		Map<String, StepResult> report = new LinkedHashMap<>();
		report.put("Login", StepResult.fail("Not executed"));
		report.put("Mi Negocio menu", StepResult.fail("Not executed"));
		report.put("Agregar Negocio modal", StepResult.fail("Not executed"));
		report.put("Administrar Negocios view", StepResult.fail("Not executed"));
		report.put("Información General", StepResult.fail("Not executed"));
		report.put("Detalles de la Cuenta", StepResult.fail("Not executed"));
		report.put("Tus Negocios", StepResult.fail("Not executed"));
		report.put("Términos y Condiciones", StepResult.fail("Not executed"));
		report.put("Política de Privacidad", StepResult.fail("Not executed"));
		return report;
	}

	private Path writeReport(final Path evidenceDir, final Map<String, StepResult> report, final String termsUrl,
			final String privacyUrl) throws IOException {
		StringBuilder content = new StringBuilder();
		content.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		content.append("================================").append(System.lineSeparator());
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			content.append("- ").append(entry.getKey()).append(": ")
					.append(entry.getValue().pass ? "PASS" : "FAIL")
					.append(" | ").append(entry.getValue().details)
					.append(System.lineSeparator());
		}
		content.append(System.lineSeparator());
		content.append("Términos y Condiciones URL: ").append(termsUrl).append(System.lineSeparator());
		content.append("Política de Privacidad URL: ").append(privacyUrl).append(System.lineSeparator());

		Path reportFile = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportFile, content.toString(), StandardCharsets.UTF_8);
		return reportFile;
	}

	private void assertEveryStepPassed(final Map<String, StepResult> report, final Path reportFile) {
		List<String> failures = report.entrySet().stream()
				.filter(entry -> !entry.getValue().pass)
				.map(entry -> entry.getKey() + " -> " + entry.getValue().details)
				.toList();
		if (!failures.isEmpty()) {
			Assert.fail("One or more workflow validations failed. Report: " + reportFile + " | Failures: " + failures);
		}
	}

	private void clickAndWait(final Locator locator, final Page page) {
		locator.first().click(new Locator.ClickOptions().setTimeout(10000));
		waitForUiLoad(page);
	}

	private Locator firstVisible(final List<Locator> candidates, final double timeoutMs) {
		for (Locator locator : candidates) {
			if (isVisible(locator, timeoutMs)) {
				return locator.first();
			}
		}
		return null;
	}

	private boolean selectGoogleAccountIfVisible(final Page page) {
		Locator account = page.getByText(Pattern.compile("(?iu)^\\s*" + Pattern.quote(GOOGLE_ACCOUNT_EMAIL) + "\\s*$"));
		if (isVisible(account, 5000)) {
			clickAndWait(account, page);
			return true;
		}
		return false;
	}

	private void waitForUiLoad(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (PlaywrightException ignored) {
			// pages with persistent connections may never become idle
		}
		page.waitForTimeout(500);
	}

	private boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.first()
					.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (PlaywrightException ex) {
			return false;
		}
	}

	private Page waitForNewPage(final BrowserContext context, final int previousPageCount, final long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() <= deadline) {
			List<Page> pages = context.pages();
			if (pages.size() > previousPageCount) {
				return pages.get(pages.size() - 1);
			}
			try {
				Thread.sleep(200);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return null;
			}
		}
		return null;
	}

	private void takeScreenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName)).setFullPage(fullPage));
	}

	private static final class StepResult {
		private final boolean pass;
		private final String details;

		private StepResult(final boolean pass, final String details) {
			this.pass = pass;
			this.details = details;
		}

		private static StepResult pass(final String details) {
			return new StepResult(true, details);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}

	private static final class LegalValidationResult {
		private final StepResult result;
		private final String url;

		private LegalValidationResult(final StepResult result, final String url) {
			this.result = result;
			this.url = url;
		}
	}
}
