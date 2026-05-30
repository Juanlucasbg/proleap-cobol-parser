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
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SaleAdsMiNegocioWorkflowTest {

	private static final String START_URL_ENV = "SALEADS_START_URL";
	private static final String HEADLESS_ENV = "SALEADS_HEADLESS";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Path EVIDENCE_DIR = Paths.get("target", "saleads-mi-negocio-evidence");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String startUrl = System.getenv(START_URL_ENV);
		Assume.assumeTrue(
				"Set environment variable " + START_URL_ENV + " to the SaleADS login page URL.",
				startUrl != null && !startUrl.trim().isEmpty());

		Files.createDirectories(EVIDENCE_DIR);
		final Map<String, StepResult> report = initReport();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(
							Boolean.parseBoolean(System.getenv().getOrDefault(HEADLESS_ENV, "true"))));
			final BrowserContext context = browser.newContext(
					new Browser.NewContextOptions().setIgnoreHTTPSErrors(true).setViewportSize(1600, 1000));
			final Page page = context.newPage();

			page.navigate(startUrl);
			waitForUiLoad(page);

			final boolean loginOk = runLoginStep(page, context, report);
			final boolean menuOk = loginOk && runMiNegocioMenuStep(page, report);
			final boolean modalOk = menuOk && runAgregarNegocioModalStep(page, report);
			final boolean administrarOk = menuOk && runAdministrarNegociosStep(page, report);
			final boolean infoOk = administrarOk && runInformacionGeneralStep(page, report);
			final boolean detallesOk = administrarOk && runDetallesCuentaStep(page, report);
			final boolean negociosOk = administrarOk && runTusNegociosStep(page, report);
			final boolean terminosOk = administrarOk && runLegalLinkStep(page, "Términos y Condiciones", "terminos", report);
			final boolean privacidadOk = administrarOk && runLegalLinkStep(page, "Política de Privacidad", "privacidad", report);

			if (!menuOk) {
				report.get("Mi Negocio menu").markBlocked("Blocked: login failed.");
			}
			if (!modalOk) {
				report.get("Agregar Negocio modal").markBlocked("Blocked: Mi Negocio menu step failed.");
			}
			if (!administrarOk) {
				report.get("Administrar Negocios view").markBlocked("Blocked: Mi Negocio menu step failed.");
			}
			if (!infoOk) {
				report.get("Información General").markBlocked("Blocked: Administrar Negocios view step failed.");
			}
			if (!detallesOk) {
				report.get("Detalles de la Cuenta").markBlocked("Blocked: Administrar Negocios view step failed.");
			}
			if (!negociosOk) {
				report.get("Tus Negocios").markBlocked("Blocked: Administrar Negocios view step failed.");
			}
			if (!terminosOk) {
				report.get("Términos y Condiciones").markBlocked("Blocked: Administrar Negocios view step failed.");
			}
			if (!privacidadOk) {
				report.get("Política de Privacidad").markBlocked("Blocked: Administrar Negocios view step failed.");
			}
		} finally {
			writeFinalReport(report);
		}

		final List<String> failures = new ArrayList<>();
		for (final StepResult stepResult : report.values()) {
			if (stepResult.status != Status.PASS) {
				failures.add(stepResult.label + " => " + stepResult.status);
			}
		}
		Assert.assertTrue("SaleADS workflow failures: " + failures, failures.isEmpty());
	}

	private static boolean runLoginStep(final Page page, final BrowserContext context, final Map<String, StepResult> report) {
		final StepResult step = report.get("Login");

		try {
			if (!isSidebarVisible(page)) {
				final Page popup = clickAndCapturePotentialPopup(page, "Sign in with Google", "Iniciar sesión con Google",
						"Inicia sesión con Google", "Continuar con Google", "Login with Google");
				final Page authPage = popup != null ? popup : page;
				maybeSelectGoogleAccount(authPage);
				if (popup != null && !popup.isClosed()) {
					waitForUiLoad(popup);
				}
				waitForUiLoad(page);
			}

			Assert.assertTrue("Main application interface should appear.", isMainInterfaceVisible(page));
			Assert.assertTrue("Left sidebar should be visible.", isSidebarVisible(page));
			step.markPass();
			step.addEvidence(captureScreenshot(page, "01-dashboard-loaded", true));
			return true;
		} catch (final Throwable error) {
			step.markFail(error.getMessage());
			step.addEvidence(captureScreenshot(page, "01-login-failed", true));
			return false;
		}
	}

	private static boolean runMiNegocioMenuStep(final Page page, final Map<String, StepResult> report) {
		final StepResult step = report.get("Mi Negocio menu");

		try {
			clickByVisibleText(page, "Negocio");
			clickByVisibleText(page, "Mi Negocio");
			validateTextVisible(page, "Agregar Negocio");
			validateTextVisible(page, "Administrar Negocios");
			step.markPass();
			step.addEvidence(captureScreenshot(page, "02-mi-negocio-menu-expanded", false));
			return true;
		} catch (final Throwable error) {
			step.markFail(error.getMessage());
			step.addEvidence(captureScreenshot(page, "02-mi-negocio-menu-failed", true));
			return false;
		}
	}

	private static boolean runAgregarNegocioModalStep(final Page page, final Map<String, StepResult> report) {
		final StepResult step = report.get("Agregar Negocio modal");

		try {
			clickByVisibleText(page, "Agregar Negocio");
			waitForUiLoad(page);
			validateTextVisible(page, "Crear Nuevo Negocio");
			validateTextVisible(page, "Nombre del Negocio");
			validateTextVisible(page, "Tienes 2 de 3 negocios");
			validateTextVisible(page, "Cancelar");
			validateTextVisible(page, "Crear Negocio");

			typeInNombreDelNegocioField(page, "Negocio Prueba Automatización");
			clickByVisibleText(page, "Cancelar");

			step.markPass();
			step.addEvidence(captureScreenshot(page, "03-agregar-negocio-modal", false));
			return true;
		} catch (final Throwable error) {
			step.markFail(error.getMessage());
			step.addEvidence(captureScreenshot(page, "03-agregar-negocio-modal-failed", true));
			return false;
		}
	}

	private static boolean runAdministrarNegociosStep(final Page page, final Map<String, StepResult> report) {
		final StepResult step = report.get("Administrar Negocios view");

		try {
			if (!isTextVisible(page, "Administrar Negocios")) {
				clickByVisibleText(page, "Mi Negocio");
			}

			clickByVisibleText(page, "Administrar Negocios");
			waitForUiLoad(page);

			validateTextVisible(page, "Información General");
			validateTextVisible(page, "Detalles de la Cuenta");
			validateTextVisible(page, "Tus Negocios");
			validateTextVisible(page, "Sección Legal");
			step.markPass();
			step.addEvidence(captureScreenshot(page, "04-administrar-negocios-page", true));
			return true;
		} catch (final Throwable error) {
			step.markFail(error.getMessage());
			step.addEvidence(captureScreenshot(page, "04-administrar-negocios-failed", true));
			return false;
		}
	}

	private static boolean runInformacionGeneralStep(final Page page, final Map<String, StepResult> report) {
		final StepResult step = report.get("Información General");

		try {
			final Locator section = sectionByHeading(page, "Información General");
			final String sectionText = section.innerText();

			final Matcher emailMatcher = EMAIL_PATTERN.matcher(sectionText);
			final boolean emailVisible = emailMatcher.find();
			final boolean planVisible = sectionText.contains("BUSINESS PLAN");
			final boolean changePlanVisible = sectionText.contains("Cambiar Plan");
			final boolean userNameVisible = containsProbableUserName(sectionText);

			Assert.assertTrue("User name should be visible.", userNameVisible);
			Assert.assertTrue("User email should be visible.", emailVisible);
			Assert.assertTrue("BUSINESS PLAN text should be visible.", planVisible);
			Assert.assertTrue("Cambiar Plan button should be visible.", changePlanVisible);

			step.markPass();
			return true;
		} catch (final Throwable error) {
			step.markFail(error.getMessage());
			step.addEvidence(captureScreenshot(page, "05-informacion-general-failed", true));
			return false;
		}
	}

	private static boolean runDetallesCuentaStep(final Page page, final Map<String, StepResult> report) {
		final StepResult step = report.get("Detalles de la Cuenta");

		try {
			final Locator section = sectionByHeading(page, "Detalles de la Cuenta");
			final String sectionText = section.innerText();
			Assert.assertTrue("'Cuenta creada' should be visible.", sectionText.contains("Cuenta creada"));
			Assert.assertTrue("'Estado activo' should be visible.", sectionText.contains("Estado activo"));
			Assert.assertTrue("'Idioma seleccionado' should be visible.", sectionText.contains("Idioma seleccionado"));
			step.markPass();
			return true;
		} catch (final Throwable error) {
			step.markFail(error.getMessage());
			step.addEvidence(captureScreenshot(page, "06-detalles-cuenta-failed", true));
			return false;
		}
	}

	private static boolean runTusNegociosStep(final Page page, final Map<String, StepResult> report) {
		final StepResult step = report.get("Tus Negocios");

		try {
			final Locator section = sectionByHeading(page, "Tus Negocios");
			final String sectionText = section.innerText();
			final boolean hasBusinessList = section.locator("li, tr, [class*=business], [data-testid*=business]").count() > 0
					|| sectionText.replace("\n", " ").trim().length() > 40;

			Assert.assertTrue("Business list should be visible.", hasBusinessList);
			Assert.assertTrue("'Agregar Negocio' button should exist.", sectionText.contains("Agregar Negocio"));
			Assert.assertTrue("'Tienes 2 de 3 negocios' should be visible.", sectionText.contains("Tienes 2 de 3 negocios"));

			step.markPass();
			return true;
		} catch (final Throwable error) {
			step.markFail(error.getMessage());
			step.addEvidence(captureScreenshot(page, "07-tus-negocios-failed", true));
			return false;
		}
	}

	private static boolean runLegalLinkStep(final Page page, final String linkText, final String screenshotKey,
			final Map<String, StepResult> report) {
		final StepResult step = report.get(linkText);

		try {
			final Page legalPage = clickAndCapturePotentialPopup(page, linkText);
			final Page targetPage = legalPage != null ? legalPage : page;
			waitForUiLoad(targetPage);

			validateTextVisible(targetPage, linkText);
			final String fullText = targetPage.locator("body").innerText();
			Assert.assertTrue("Legal content text should be visible.", fullText != null && fullText.trim().length() > 150);

			step.finalUrl = targetPage.url();
			step.addEvidence(captureScreenshot(targetPage, "08-" + screenshotKey + "-page", true));
			step.markPass();

			if (legalPage != null) {
				if (!legalPage.isClosed()) {
					legalPage.close();
				}
				page.bringToFront();
			} else {
				page.goBack();
				waitForUiLoad(page);
			}

			return true;
		} catch (final Throwable error) {
			step.markFail(error.getMessage());
			step.addEvidence(captureScreenshot(page, "08-" + screenshotKey + "-failed", true));
			return false;
		}
	}

	private static Map<String, StepResult> initReport() {
		final Map<String, StepResult> report = new LinkedHashMap<>();
		final List<String> labels = Arrays.asList(
				"Login",
				"Mi Negocio menu",
				"Agregar Negocio modal",
				"Administrar Negocios view",
				"Información General",
				"Detalles de la Cuenta",
				"Tus Negocios",
				"Términos y Condiciones",
				"Política de Privacidad");
		for (final String label : labels) {
			report.put(label, new StepResult(label));
		}
		return report;
	}

	private static void writeFinalReport(final Map<String, StepResult> report) throws IOException {
		final Path reportPath = EVIDENCE_DIR.resolve("final-report.txt");
		final StringBuilder content = new StringBuilder();
		content.append("SaleADS Mi Negocio Workflow Report\n");
		content.append("Generated At (UTC): ").append(DateTimeFormatter.ISO_INSTANT.format(Instant.now())).append("\n\n");
		content.append("| Step | Status | Final URL | Evidence |\n");
		content.append("| --- | --- | --- | --- |\n");

		for (final StepResult stepResult : report.values()) {
			content.append("| ")
					.append(stepResult.label)
					.append(" | ")
					.append(stepResult.status)
					.append(" | ")
					.append(stepResult.finalUrl == null ? "-" : stepResult.finalUrl)
					.append(" | ")
					.append(stepResult.evidencePaths.isEmpty() ? "-" : String.join(", ", stepResult.evidencePaths))
					.append(" |\n");
			for (final String note : stepResult.notes) {
				content.append("  - ").append(note).append("\n");
			}
		}

		Files.write(reportPath, content.toString().getBytes(StandardCharsets.UTF_8));
	}

	private static boolean containsProbableUserName(final String sectionText) {
		final String[] lines = sectionText.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty() || line.contains("@")) {
				continue;
			}
			if ("Información General".equalsIgnoreCase(line) || "BUSINESS PLAN".equalsIgnoreCase(line)
					|| "Cambiar Plan".equalsIgnoreCase(line)) {
				continue;
			}
			if (line.matches(".*[A-Za-zÁÉÍÓÚÑáéíóúñ]{3,}.*")) {
				return true;
			}
		}
		return false;
	}

	private static Locator sectionByHeading(final Page page, final String headingText) {
		final Locator section = page.locator("section,div").filter(new Locator.FilterOptions().setHasText(headingText)).first();
		Assert.assertTrue("Section not found for heading: " + headingText, section.count() > 0);
		return section;
	}

	private static void typeInNombreDelNegocioField(final Page page, final String value) {
		Locator input = page.getByLabel("Nombre del Negocio");
		if (input.count() == 0) {
			input = page.getByPlaceholder("Nombre del Negocio");
		}
		if (input.count() == 0) {
			input = page.locator("input[name*=nombre i], input[id*=nombre i], input").first();
		}
		Assert.assertTrue("Nombre del Negocio input field was not found.", input.count() > 0);
		input.first().click();
		input.first().fill(value);
		waitForUiLoad(page);
	}

	private static void maybeSelectGoogleAccount(final Page authPage) {
		try {
			final Locator account = authPage.getByText(GOOGLE_ACCOUNT_EMAIL,
					new Page.GetByTextOptions().setExact(true));
			if (account.count() > 0 && account.first().isVisible()) {
				account.first().click();
				waitForUiLoad(authPage);
			}
		} catch (final PlaywrightException ignored) {
			// Account selector does not always appear in every environment/session.
		}
	}

	private static Page clickAndCapturePotentialPopup(final Page page, final String... labels) {
		try {
			return page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(5000), () -> clickByVisibleText(page, labels));
		} catch (final PlaywrightException timeoutOrNoPopup) {
			clickByVisibleText(page, labels);
			return null;
		}
	}

	private static void clickByVisibleText(final Page page, final String... labels) {
		for (final String label : labels) {
			if (tryClick(page, page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(label)))) {
				return;
			}
			if (tryClick(page, page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(label)))) {
				return;
			}
			if (tryClick(page, page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(label)))) {
				return;
			}
			if (tryClick(page, page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(label)))) {
				return;
			}
			if (tryClick(page, page.getByText(label, new Page.GetByTextOptions().setExact(true)))) {
				return;
			}
			if (tryClick(page, page.getByText(label))) {
				return;
			}
		}
		throw new AssertionError("None of the expected clickable labels were found: " + Arrays.toString(labels));
	}

	private static boolean tryClick(final Page page, final Locator locator) {
		try {
			if (locator.count() == 0) {
				return false;
			}
			final Locator first = locator.first();
			first.waitFor(new Locator.WaitForOptions().setTimeout(3000).setState(WaitForSelectorState.VISIBLE));
			first.scrollIntoViewIfNeeded();
			first.click(new Locator.ClickOptions().setTimeout(5000));
			waitForUiLoad(page);
			return true;
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private static boolean isTextVisible(final Page page, final String text) {
		try {
			final Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true));
			return exact.count() > 0 && exact.first().isVisible();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private static void validateTextVisible(final Page page, final String text) {
		Assert.assertTrue("Expected visible text not found: " + text, isTextVisible(page, text) || page.getByText(text).count() > 0);
	}

	private static boolean isMainInterfaceVisible(final Page page) {
		return page.locator("main,[role=main],aside,nav").count() > 0;
	}

	private static boolean isSidebarVisible(final Page page) {
		try {
			return page.locator("aside:visible, nav:visible").count() > 0 || isTextVisible(page, "Negocio");
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private static String captureScreenshot(final Page page, final String fileLabel, final boolean fullPage) {
		try {
			final String safeName = fileLabel.replaceAll("[^A-Za-z0-9._-]", "_");
			final Path screenshotPath = EVIDENCE_DIR.resolve(safeName + ".png");
			page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
			return screenshotPath.toString();
		} catch (final PlaywrightException captureError) {
			return "screenshot_failed:" + captureError.getMessage();
		}
	}

	private static void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final PlaywrightException ignored) {
			// Some click actions can happen without navigation.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (final PlaywrightException ignored) {
			// Do not fail because some pages keep long polling open.
		}
		page.waitForTimeout(750);
	}

	private enum Status {
		PASS, FAIL, BLOCKED
	}

	private static final class StepResult {
		private final String label;
		private Status status;
		private String finalUrl;
		private final List<String> notes = new ArrayList<>();
		private final List<String> evidencePaths = new ArrayList<>();

		private StepResult(final String label) {
			this.label = label;
			this.status = Status.BLOCKED;
		}

		private void markPass() {
			this.status = Status.PASS;
		}

		private void markFail(final String message) {
			this.status = Status.FAIL;
			if (message != null && !message.trim().isEmpty()) {
				this.notes.add(message);
			}
		}

		private void markBlocked(final String message) {
			if (this.status == Status.PASS || this.status == Status.FAIL) {
				return;
			}
			this.status = Status.BLOCKED;
			if (message != null && !message.trim().isEmpty()) {
				this.notes.add(message);
			}
		}

		private void addEvidence(final String evidencePath) {
			if (evidencePath != null && !evidencePath.trim().isEmpty()) {
				this.evidencePaths.add(evidencePath);
			}
		}
	}
}
